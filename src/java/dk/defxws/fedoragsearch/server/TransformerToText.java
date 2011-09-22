//$Id: TransformerToText.java 7837 2008-11-21 11:39:09Z gertsp $
/*
 * <p><b>License and Copyright: </b>The contents of this file is subject to the
 * same open source license as the Fedora Repository System at www.fedora-commons.org
 * Copyright &copy; 2006, 2007, 2008 by The Technical University of Denmark.
 * All rights reserved.</p>
 */
package dk.defxws.fedoragsearch.server;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import javax.xml.transform.stream.StreamSource;

import org.apache.lucene.demo.html.HTMLParser;
import org.apache.pdfbox.cos.COSDocument;
import org.apache.pdfbox.encryption.DocumentEncryption;
import org.apache.pdfbox.exceptions.CryptographyException;
import org.apache.pdfbox.exceptions.InvalidPasswordException;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.util.PDFTextStripper;
import org.apache.poi.hslf.extractor.PowerPointExtractor;
import org.apache.poi.hssf.extractor.ExcelExtractor;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.xslf.extractor.XSLFPowerPointExtractor;
import org.apache.poi.xssf.extractor.XSSFExcelExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.escidoc.sb.common.Constants;
import dk.defxws.fedoragsearch.server.errors.GenericSearchException;
import dk.defxws.fedoragsearch.server.utils.IOUtils;
import dk.defxws.fedoragsearch.server.utils.Stream;

/**
 * performs transformations from formatted documents to text
 * 
 * @author  gsp@dtv.dk
 * @version 
 */
public final class TransformerToText {
    
    private static final Logger logger =
        LoggerFactory.getLogger(TransformerToText.class);
    
    public static final String[] handledMimeTypes = { "text/plain", "text/xml",
        "text/html", "application/xml", "application/pdf",
        "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-powerpoint", "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"};
    
    private static final String genericTextExtractionErrorString = " textnotextractable";
    
    private static final String textTextExtractionErrorString = 
    	"textfromtextfilenotextractable" + genericTextExtractionErrorString;
    
    private static final String xmlTextExtractionErrorString = 
    	"textfromxmlfilenotextractable" + genericTextExtractionErrorString;
    
    private static final String htmlTextExtractionErrorString = 
    	"textfromhtmlfilenotextractable" + genericTextExtractionErrorString;
    
    private static final String pdfTextExtractionErrorString = 
    	"textfrompdffilenotextractable" + genericTextExtractionErrorString;
    
    private static final String docTextExtractionErrorString = 
    	"textfromdocfilenotextractable" + genericTextExtractionErrorString;
    
    private static final String docxTextExtractionErrorString = 
    	"textfromdocxfilenotextractable" + genericTextExtractionErrorString;
    
    private static final String pptTextExtractionErrorString = 
    	"textfrompptfilenotextractable" + genericTextExtractionErrorString;
    
    private static final String pptxTextExtractionErrorString = 
    	"textfrompptxfilenotextractable" + genericTextExtractionErrorString;
    
    private static final String xlsTextExtractionErrorString = 
    	"textfromxlsfilenotextractable" + genericTextExtractionErrorString;
    
    private static final String xlsxTextExtractionErrorString = 
    	"textfromxlsxfilenotextractable" + genericTextExtractionErrorString;
    
    private TransformerToText() {
    }

    public static Stream getText(InputStream doc, String mimetype)
            throws GenericSearchException {
        try {
            if (mimetype.equals("text/plain")) {
                return getTextFromText(doc);
            } else if (mimetype.equals("text/xml")
                    || mimetype.equals("application/xml")) {
                return getTextFromXML(doc);
            } else if (mimetype.equals("text/html")) {
                return getTextFromHTML(doc);
            } else if (mimetype.equals("application/pdf")) {
                return getTextFromPDF(doc);
            } else if (mimetype.equals("application/ps")) {
                return new Stream(); 
            } else if (mimetype.equals("application/msword")) {
                return getTextFromDOC(doc);
            } else if (mimetype.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")) {
                return getTextFromDOCX(doc);
            } else if (mimetype.equals("application/vnd.ms-powerpoint")) {
                return getTextFromPPT(doc);
            } else if (mimetype.equals("application/vnd.openxmlformats-officedocument.presentationml.presentation")) {
                return getTextFromPPTX(doc);
            } else if (mimetype.equals("application/vnd.ms-excel")) {
                return getTextFromXLS(doc);
            } else if (mimetype.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) {
                return getTextFromXLSX(doc);
            } else
                return new Stream();
        } catch (Exception e) {
            if (Boolean.parseBoolean(
                Config.getCurrentConfig().getIgnoreTextExtractionErrors())) {
                logger.warn("", e);
                return createErrorStream("textfromfilenotextractable");
            } else {
                throw new GenericSearchException(e.toString());
            }
        }
    }

    private static Stream getTextFromText(InputStream input)
    throws GenericSearchException {
        boolean errorFlag = Boolean.parseBoolean(
                Config.getCurrentConfig().getIgnoreTextExtractionErrors());
        Stream docText = new Stream();
        try {
            IOUtils.copy(input, docText);
            docText.lock();
        } catch(IOException e) {
            if (errorFlag) {
            	logger.warn("", e);
                return createErrorStream(textTextExtractionErrorString);
            } else {
                throw new GenericSearchException(e.toString());
            }
        }
        return docText;
    }


	private static Stream getTextFromXML(InputStream doc)
			throws GenericSearchException {
        boolean errorFlag = Boolean.parseBoolean(
                Config.getCurrentConfig().getIgnoreTextExtractionErrors());
		InputStreamReader isr = null;
		Stream docText;
		try {
			isr = new InputStreamReader(doc, "UTF-8");
			docText = (new GTransformer()).transform("/index/textFromXml",
					new StreamSource(isr));
			// TODO
			// docText.delete(0, docText.indexOf('>')+1);
		} catch (Exception e) {
            if (errorFlag) {
            	logger.warn("", e);
                return createErrorStream(xmlTextExtractionErrorString);
            } else {
                throw new GenericSearchException(e.toString());
            }
		}
		return docText;
	}

    private static Stream getTextFromHTML(InputStream doc)
    throws GenericSearchException {
        boolean errorFlag = Boolean.parseBoolean(
                Config.getCurrentConfig().getIgnoreTextExtractionErrors());
        Stream docText = new Stream();
        try {
            HTMLParser htmlParser = new HTMLParser(doc);
            IOUtils.copy(htmlParser.getReader(), docText);
            docText.lock();
        } catch (Exception e) {
            if (errorFlag) {
            	logger.warn("", e);
                return createErrorStream(htmlTextExtractionErrorString);
            } else {
                throw new GenericSearchException(e.toString());
            }
        }
        return docText;
    }

    private static Stream getTextFromDOC(InputStream doc)
            throws GenericSearchException {
        boolean errorFlag = Boolean.parseBoolean(
                Config.getCurrentConfig().getIgnoreTextExtractionErrors());
        WordExtractor wordExtractor = null;
        try {
    		wordExtractor = new WordExtractor(doc);
            StringBuffer buffer = new StringBuffer(wordExtractor.getText().trim());
            Stream stream = new Stream();
            stream.write(buffer.toString().getBytes(Constants.XML_CHARACTER_ENCODING));
            stream.lock();
            return stream;
        } catch (Exception e) {
            if (errorFlag) {
            	logger.warn("", e);
                return createErrorStream(docTextExtractionErrorString);
            } else {
                throw new GenericSearchException("cannot parse doc-file",
                        e);
            }
        } finally {
        	wordExtractor = null;
        }
    }

	private static Stream getTextFromDOCX(InputStream doc)
			throws GenericSearchException {
		boolean errorFlag = Boolean.parseBoolean(Config.getCurrentConfig()
				.getIgnoreTextExtractionErrors());
		XWPFWordExtractor wordExtractor = null;
		try {
			wordExtractor = new XWPFWordExtractor(OPCPackage.open(doc));
	        StringBuffer buffer = new StringBuffer(wordExtractor.getText().trim());
			Stream stream = new Stream();
			stream.write(buffer.toString().getBytes(
					Constants.XML_CHARACTER_ENCODING));
			stream.lock();
			return stream;
		} catch (Exception e) {
			if (errorFlag) {
				logger.warn("", e);
				return createErrorStream(docxTextExtractionErrorString);
			} else {
				throw new GenericSearchException("cannot parse docx-file", e);
			}
		} finally {
			wordExtractor = null;
		}
	}

	private static Stream getTextFromPPT(InputStream doc)
			throws GenericSearchException {
		boolean errorFlag = Boolean.parseBoolean(Config.getCurrentConfig()
				.getIgnoreTextExtractionErrors());
		PowerPointExtractor powerPointExtractor = null;
		try {
			powerPointExtractor = new PowerPointExtractor(doc);
	        StringBuffer buffer = new StringBuffer(powerPointExtractor.getText(true, true).trim());
			Stream stream = new Stream();
			stream.write(buffer.toString().getBytes(
					Constants.XML_CHARACTER_ENCODING));
			stream.lock();
			return stream;
		} catch (Exception e) {
			if (errorFlag) {
				logger.warn("", e);
				return createErrorStream(pptTextExtractionErrorString);
			} else {
				throw new GenericSearchException("cannot parse ppt-file", e);
			}
		} finally {
			powerPointExtractor = null;
		}
	}

	private static Stream getTextFromPPTX(InputStream doc)
			throws GenericSearchException {
		boolean errorFlag = Boolean.parseBoolean(Config.getCurrentConfig()
				.getIgnoreTextExtractionErrors());
		XSLFPowerPointExtractor powerPointExtractor = null;
		try {
			powerPointExtractor = new XSLFPowerPointExtractor(OPCPackage.open(doc));
	        StringBuffer buffer = new StringBuffer(powerPointExtractor.getText(true, true).trim());
			Stream stream = new Stream();
			stream.write(buffer.toString().getBytes(
					Constants.XML_CHARACTER_ENCODING));
			stream.lock();
			return stream;
		} catch (Exception e) {
			if (errorFlag) {
				logger.warn("", e);
				return createErrorStream(pptxTextExtractionErrorString);
			} else {
				throw new GenericSearchException("cannot parse pptx-file", e);
			}
		} finally {
			powerPointExtractor = null;
		}
	}

	private static Stream getTextFromXLS(InputStream doc)
			throws GenericSearchException {
		boolean errorFlag = Boolean.parseBoolean(Config.getCurrentConfig()
				.getIgnoreTextExtractionErrors());
		HSSFWorkbook wb = null;
		ExcelExtractor excelExtractor = null;
		try {
			wb = new HSSFWorkbook(new POIFSFileSystem(doc));
			excelExtractor = new ExcelExtractor(wb);
	        StringBuffer buffer = new StringBuffer(excelExtractor.getText().trim());
			Stream stream = new Stream();
			stream.write(buffer.toString().getBytes(
					Constants.XML_CHARACTER_ENCODING));
			stream.lock();
			return stream;
		} catch (Exception e) {
			if (errorFlag) {
				logger.warn("", e);
				return createErrorStream(xlsTextExtractionErrorString);
			} else {
				throw new GenericSearchException("cannot parse xls-file", e);
			}
		} finally {
			excelExtractor = null;
			wb = null;
		}
	}

	private static Stream getTextFromXLSX(InputStream doc)
			throws GenericSearchException {
		boolean errorFlag = Boolean.parseBoolean(Config.getCurrentConfig()
				.getIgnoreTextExtractionErrors());
		XSSFExcelExtractor excelExtractor = null;
		try {
			excelExtractor = new XSSFExcelExtractor(OPCPackage.open(doc));
	        StringBuffer buffer = new StringBuffer(excelExtractor.getText().trim());
			Stream stream = new Stream();
			stream.write(buffer.toString().getBytes(
					Constants.XML_CHARACTER_ENCODING));
			stream.lock();
			return stream;
		} catch (Exception e) {
			if (errorFlag) {
				logger.warn("", e);
				return createErrorStream(xlsxTextExtractionErrorString);
			} else {
				throw new GenericSearchException("cannot parse xlsx-file", e);
			}
		} finally {
			excelExtractor = null;
		}
	}

    private static Stream getTextFromPDF(InputStream doc)
            throws GenericSearchException {
        String textExtractorCommand = Config.getCurrentConfig().getPdfTextExtractorCommand();
        if (textExtractorCommand == null || textExtractorCommand.equals("")) {
            return getTextFromPDFWithPdfBox(doc);
        } 
        else if (textExtractorCommand.equals("iText")) {
            return getTextFromPDFWithItext(doc);
        }
        else {
            return getTextFromPDFWithExternalTool(doc);
        }
    }
    
    private static COSDocument parseDocument(InputStream is)
    throws IOException {
        PDFParser parser = new PDFParser(is);
        parser.parse();
        return parser.getDocument();
    }
    
    private static void closeCOSDocument(COSDocument cosDoc) {
        if (cosDoc != null) {
            try {
                cosDoc.close();
            }
            catch (IOException e) {
            }
        }
    }
    
    private static Stream getTextFromPDFWithPdfBox(InputStream doc)
    throws GenericSearchException  {
        COSDocument cosDoc = null;
        String password = "";
        boolean errorFlag = Boolean.parseBoolean(
                Config.getCurrentConfig().getIgnoreTextExtractionErrors());
        try {
            cosDoc = parseDocument(doc);
        } catch (IOException e) {
            closeCOSDocument(cosDoc);
            if (errorFlag) {
            	logger.warn("", e);
                return createErrorStream(pdfTextExtractionErrorString);
            } else {
                throw new GenericSearchException("Cannot parse PDF document", e);
            }
        }

        // decrypt the PDF document, if it is encrypted
        try {
            if (cosDoc.isEncrypted()) {
                DocumentEncryption decryptor = new DocumentEncryption(cosDoc);
                decryptor.decryptDocument(password);
            }
        } catch (CryptographyException e) {
            closeCOSDocument(cosDoc);
            if (errorFlag) {
            	logger.warn("", e);
                return createErrorStream(pdfTextExtractionErrorString);
            } else {
                throw new GenericSearchException("Cannot decrypt PDF document",
                        e);
            }
        } catch (InvalidPasswordException e) {
            closeCOSDocument(cosDoc);
            if (errorFlag) {
            	logger.warn("", e);
                return createErrorStream(pdfTextExtractionErrorString);
            } else {
                throw new GenericSearchException("Cannot decrypt PDF document",
                        e);
            }
        } catch (IOException e) {
            closeCOSDocument(cosDoc);
            if (errorFlag) {
            	logger.warn("", e);
                return createErrorStream(pdfTextExtractionErrorString);
            } else {
                throw new GenericSearchException("Cannot decrypt PDF document",
                        e);
            }
        }
        Stream docText = new Stream();
        // extract PDF document's textual content
        try {
            PDFTextStripper stripper = new PDFTextStripper();
            // TODO: Unterstützt PDFTextStripper keine Streams?
            docText.write(stripper.getText(new PDDocument(cosDoc)).getBytes(Constants.XML_CHARACTER_ENCODING));
            docText.lock();
        } catch (Exception e) {
            closeCOSDocument(cosDoc);
            if (errorFlag) {
            	logger.warn("", e);
                return createErrorStream(pdfTextExtractionErrorString);
            } else {
                throw new GenericSearchException(
                        "Cannot parse PDF document", e);
            }
        }
        closeCOSDocument(cosDoc);
        return docText;
    }

    private static Stream getTextFromPDFWithItext(InputStream doc) throws GenericSearchException {
        Stream docText = new Stream();
        boolean errorFlag = Boolean.parseBoolean(
            Config.getCurrentConfig().getIgnoreTextExtractionErrors());
        try {
//            docText.append(" ");
//            PdfReader reader = new PdfReader(doc);
//            for (int i = 1; i <= reader.getNumberOfPages(); i++) {
//                docText.append(PdfTextExtractor.getTextFromPage(reader, i)).append(" ");
//            }
        	docText.write(" ".getBytes(Constants.XML_CHARACTER_ENCODING)); // TODO: Warum ist der obere Code auskommentiert?
            docText.lock();
        } catch (Exception e) {
            if (errorFlag) {
            	logger.warn("", e);
                return createErrorStream(pdfTextExtractionErrorString);
            } else {
                throw new GenericSearchException(
                        "Cannot parse PDF document", e);
            }
        }
        return docText;
    }

    private static Stream getTextFromPDFWithExternalTool(InputStream doc) throws GenericSearchException {
        boolean errorFlag = Boolean.parseBoolean(
            Config.getCurrentConfig().getIgnoreTextExtractionErrors());
        Stream textBuffer = new Stream();
        String inputFileName = null;
        String outputFileName = null;
        FileOutputStream fop = null;
        FileInputStream fileIn = null;
        BufferedReader in = null;
        BufferedReader stdIn = null;
        BufferedReader errIn = null;
        Process p = null;
        try {
            long currMillies = System.currentTimeMillis();
            String escidocHome = System.getProperty("ESCIDOC_HOME");
            if (escidocHome != null) {
                escidocHome += "/";
            } else {
                escidocHome = "";
            }
            inputFileName = escidocHome + currMillies + ".pdf";
            outputFileName = escidocHome + currMillies + ".txt";

            //write pdf-bytes to file
            File f = new File(inputFileName);
            fop = new FileOutputStream(f);
            if (doc != null) {
                byte[] bytes = new byte[0xFFFF];
                int i = -1;
                while ((i = doc.read(bytes)) > -1) {
                    fop.write(bytes, 0, i);
                }
                fop.flush();
                fop.close();
            }
            
            //convert pdf-file to text-file
            String command = Config.getCurrentConfig().getPdfTextExtractorCommand();
            logger.info(
                "try to get text from pdf with external command-tool: " 
                    + command);
            if (command == null || command.equals("")) {
                throw new IOException(
                        "PdfTextExtractor command not found in config");
            }
            command = command.replace("<outputfile>", outputFileName);
            command = command.replace("<inputfile>", inputFileName);
            p = Runtime.getRuntime().exec(command);

            //wait until process is finished
            stdIn = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), Constants.XML_CHARACTER_ENCODING));
            errIn = new BufferedReader(
                    new InputStreamReader(p.getErrorStream(), Constants.XML_CHARACTER_ENCODING));
            StringBuffer errBuf = new StringBuffer("");
            long time = System.currentTimeMillis();
            while (true) {
                try {
                    p.exitValue();
                    break;
                } catch (Exception e) {
                    Thread.sleep(200);
                    int c;
                    if (stdIn.ready()) {
                       while ((stdIn.read()) > -1) {
                       }
                    }
                    if (errIn.ready()) {
                       while ((c = errIn.read()) > -1) {
                           errBuf.append((char)c);
                       }
                    }
                    if (System.currentTimeMillis() - time > 60000) {
                        throw new IOException(
                                "couldnt extract text from pdf, timeout reached");
                    }
                }
            }
            if (errBuf.length() > 0) {
                logger.warn("error while transforming pdf "
                        + "to text with external tool:\n" + errBuf);
            }
            
            //read textfile
            fileIn = new FileInputStream(outputFileName);            
            in = new BufferedReader(
                    new InputStreamReader(fileIn, Constants.XML_CHARACTER_ENCODING));
            String str = new String("");
            while ((str = in.readLine()) != null) {
                textBuffer.write(str.getBytes(Constants.XML_CHARACTER_ENCODING));
                textBuffer.write(" ".getBytes(Constants.XML_CHARACTER_ENCODING)); // TODO: Wofür sind Spaces nötig?
            }
            textBuffer.lock();
            fileIn.close();
        } catch (Exception e) {
            if (errorFlag) {
            	logger.warn("", e);
                return createErrorStream(pdfTextExtractionErrorString);
            } else {
                throw new GenericSearchException(
                        "Cannot parse PDF document", e);
            }
        } finally {
            //close Streams
            if (fop != null) {
                try {
                    fop.close();
                } catch (Exception e) {}
            }
            if (fileIn != null) {
                try {
                    fileIn.close();
                } catch (Exception e) {}
            }
            if (in != null) {
                try {
                    in.close();
                } catch (Exception e) {}
            }
            if (stdIn != null) {
                try {
                    stdIn.close();
                } catch (Exception e) {}
            }
            if (errIn != null) {
                try {
                    errIn.close();
                } catch (Exception e) {}
            }
            
            //close process
            if (p != null) {
                try {
                    p.getInputStream().close();
                } catch (Exception e) {}
                try {
                    p.getErrorStream().close();
                } catch (Exception e) {}
                try {
                    p.destroy();
                } catch (Exception e) {}
            }
            
            //delete files again
            try {
                File inputFile = new File(inputFileName);
                inputFile.delete();
            } catch (Exception e) {}
            try {
                File outputFile = new File(outputFileName);
                outputFile.delete();
            } catch (Exception e) {}
        }
        
        return textBuffer;
    }
    
    private String byteToHex(byte b) {
        // Returns hex String representation of byte b
        char hexDigit[] = {
           '0', '1', '2', '3', '4', '5', '6', '7',
           '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
        };
        char[] array = { hexDigit[(b >> 4) & 0x0f], hexDigit[b & 0x0f] };
        return new String(array);
     }

     public static void main(String[] args) {
    }

    private static Stream createErrorStream(String errorMessage) throws GenericSearchException {
        Stream errorStream = new Stream();
        try {
            errorStream.write(errorMessage.getBytes(Constants.XML_CHARACTER_ENCODING));
            errorStream.lock();
        } catch(IOException e) {
            throw new GenericSearchException(e.toString());
        }
        return errorStream;
    }

}
