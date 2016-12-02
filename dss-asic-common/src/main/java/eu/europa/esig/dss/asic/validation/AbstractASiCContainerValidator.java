package eu.europa.esig.dss.asic.validation;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.europa.esig.dss.ASiCContainerType;
import eu.europa.esig.dss.DSSDocument;
import eu.europa.esig.dss.DSSException;
import eu.europa.esig.dss.DSSUnsupportedOperationException;
import eu.europa.esig.dss.asic.ASiCExtractResult;
import eu.europa.esig.dss.asic.ASiCUtils;
import eu.europa.esig.dss.asic.AbstractASiCContainerExtractor;
import eu.europa.esig.dss.utils.Utils;
import eu.europa.esig.dss.validation.AdvancedSignature;
import eu.europa.esig.dss.validation.DocumentValidator;
import eu.europa.esig.dss.validation.SignedDocumentValidator;
import eu.europa.esig.dss.validation.policy.ValidationPolicy;
import eu.europa.esig.dss.validation.reports.Reports;

public abstract class AbstractASiCContainerValidator extends SignedDocumentValidator {

	private static final Logger LOG = LoggerFactory.getLogger(AbstractASiCContainerValidator.class);

	private ASiCExtractResult extractResult;

	private ASiCContainerType containerType;

	private String zipComment;

	/**
	 * Default constructor used with reflexion (see SignedDocumentValidator)
	 */
	private AbstractASiCContainerValidator() {
		super(null);
		this.document = null;
	}

	protected AbstractASiCContainerValidator(final DSSDocument document) {
		super(null);
		this.document = document;
	}

	protected void analyseEntries() {
		AbstractASiCContainerExtractor extractor = getArchiveExtractor();
		extractResult = extractor.extract();

		extractZipComment(document);
		containerType = ASiCUtils.getContainerType(document, extractResult.getMimeTypeDocument(), zipComment);
	}

	abstract AbstractASiCContainerExtractor getArchiveExtractor();

	private void extractZipComment(final DSSDocument document) {
		InputStream is = null;
		try {
			is = document.openStream();
			byte[] buffer = Utils.toByteArray(is);
			final int len = buffer.length;
			final byte[] magicDirEnd = { 0x50, 0x4b, 0x05, 0x06 };

			// Check the buffer from the end
			for (int ii = len - magicDirEnd.length - 22; ii >= 0; ii--) {
				boolean isMagicStart = true;
				for (int jj = 0; jj < magicDirEnd.length; jj++) {
					if (buffer[ii + jj] != magicDirEnd[jj]) {
						isMagicStart = false;
						break;
					}
				}
				if (isMagicStart) {
					// Magic Start found!
					int commentLen = buffer[ii + 20] + buffer[ii + 21] * 256;
					int realLen = len - ii - 22;
					if (commentLen != realLen) {
						LOG.warn("WARNING! ZIP comment size mismatch: directory says len is " + commentLen + ", but file ends after " + realLen + " bytes!");
					}
					zipComment = new String(buffer, ii + 22, Math.min(commentLen, realLen));
				}
			}
		} catch (IOException e) {
			throw new DSSException("Unable to extract the ZIP comment", e);
		} finally {
			Utils.closeQuietly(is);
		}
	}

	public ASiCContainerType getContainerType() {
		return containerType;
	}

	@Override
	public List<AdvancedSignature> getSignatures() {

		ensureCertificatePoolInitialized();

		List<AdvancedSignature> allSignatures = new ArrayList<AdvancedSignature>();
		List<DocumentValidator> validators = getValidators();
		for (DocumentValidator documentValidator : validators) {
			allSignatures.addAll(documentValidator.getSignatures());
		}
		return allSignatures;
	}

	abstract List<DocumentValidator> getValidators();

	@Override
	public Reports validateDocument(final ValidationPolicy validationPolicy) {

		ensureCertificatePoolInitialized();

		Reports first = null;
		Reports previous = null;

		List<DocumentValidator> validators = getValidators();
		for (DocumentValidator validator : validators) {
			Reports currentReport = validator.validateDocument(validationPolicy);
			if (first == null) {
				first = currentReport;
			} else {
				previous.setNextReport(currentReport);
			}
			previous = currentReport;
		}

		return first;
	}

	protected List<DSSDocument> getSignatureDocuments() {
		return extractResult.getSignatureDocuments();
	}

	protected List<DSSDocument> getSignedDocuments() {
		return extractResult.getSignedDocuments();
	}

	protected List<DSSDocument> getManifestDocuments() {
		return extractResult.getManifestDocuments();
	}

	public ContainerAnalysis getContainerAnalysis() {
		ContainerAnalysis analysis = new ContainerAnalysis();
		analysis.setZipFile(true);
		if (extractResult.getMimeTypeDocument() != null) {
			analysis.setMimetypeFilePresent(true);
		}
		analysis.setNbSignatureFiles(extractResult.getSignatureDocuments().size());
		analysis.setNbManifestFiles(extractResult.getManifestDocuments().size());
		analysis.setNbDataFiles(extractResult.getSignedDocuments().size());
		analysis.setZipComment(zipComment);
		return analysis;
	}

	@Override
	public List<DSSDocument> getOriginalDocuments(String signatureId) throws DSSException {
		// TODO
		throw new DSSUnsupportedOperationException("This method is not applicable for this kind of file!");
	}

}
