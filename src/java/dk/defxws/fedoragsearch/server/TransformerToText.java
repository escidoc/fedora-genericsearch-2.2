//$Id: TransformerToText.java 7837 2008-11-21 11:39:09Z gertsp $
/*
 * <p><b>License and Copyright: </b>The contents of this file is subject to the
 * same open source license as the Fedora Repository System at www.fedora-commons.org
 * Copyright &copy; 2006, 2007, 2008 by The Technical University of Denmark.
 * All rights reserved.</p>
 */
package dk.defxws.fedoragsearch.server;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;

import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.stream.StreamSource;

import org.apache.log4j.Logger;
import org.apache.lucene.demo.html.HTMLParser;
import org.apache.pdfbox.cos.COSDocument;
import org.apache.pdfbox.encryption.DocumentEncryption;
import org.apache.pdfbox.exceptions.CryptographyException;
import org.apache.pdfbox.exceptions.InvalidPasswordException;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.util.PDFTextStripper;
import org.apache.poi.hwpf.extractor.WordExtractor;

import dk.defxws.fedoragsearch.server.errors.GenericSearchException;

/**
 * performs transformations from formatted documents to text
 * 
 * @author  gsp@dtv.dk
 * @version 
 */
public class TransformerToText {
    
    private static final Logger logger =
        Logger.getLogger(TransformerToText.class);
    
    public static final String[] handledMimeTypes = { "text/plain", "text/xml",
        "text/html", "application/xml", "application/pdf",
        "application/msword" };
    
    public TransformerToText() {
    }
    
    /**
     * 
     * 
     * @throws TransformerConfigurationException,
     *             TransformerException.
     */
    public StringBuffer getText(byte[] doc, String mimetype)
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
                return new StringBuffer();
            } else if (mimetype.equals("application/msword")) {
                return getTextFromDOC(doc);
            } else
                return new StringBuffer();
        } catch (Throwable e) {
            if (Boolean.parseBoolean(
                Config.getCurrentConfig().getIgnoreTextExtractionErrors())) {
                logger.warn(e);
                return new StringBuffer("textfromfilenotextractable");
            } else {
                throw new GenericSearchException(e.toString());
            }
        }
    }

    /**
     * 
     *
     * @throws GenericSearchException.
     */
    private StringBuffer getTextFromText(byte[] doc) 
    throws GenericSearchException {
        StringBuffer docText = new StringBuffer();
        InputStreamReader isr = null;
        try {
            isr = new InputStreamReader(new ByteArrayInputStream(doc), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new GenericSearchException("encoding exception", e);
        }
        try {
            int c = isr.read();
            while (c>-1) {
                docText.append((char)c);
                c=isr.read();
            }
        } catch (IOException e) {
            throw new GenericSearchException(e.toString());
        }
        return docText;
    }

/**
 * 
 *
 * @throws GenericSearchException.
 */
private StringBuffer getTextFromXML(byte[] doc) 
throws GenericSearchException {
    InputStreamReader isr = null;
    try {
        isr = new InputStreamReader(new ByteArrayInputStream(doc), "UTF-8");
    } catch (UnsupportedEncodingException e) {
        throw new GenericSearchException("encoding exception", e);
    }
    StringBuffer docText = (new GTransformer()).transform(
            Config.getDefaultConfigName()+ "/index/textFromXml", 
            new StreamSource(isr));
    docText.delete(0, docText.indexOf(">")+1);
    return docText;
}
    
    /**
     * 
     *
     * @throws GenericSearchException.
     */
    private StringBuffer getTextFromHTML(byte[] doc) 
    throws GenericSearchException {
        StringBuffer docText = new StringBuffer();
        HTMLParser htmlParser = new HTMLParser(new ByteArrayInputStream(doc));
        try {
            InputStreamReader isr = (InputStreamReader) htmlParser.getReader();
            int c = isr.read();
            while (c>-1) {
                docText.append((char)c);
                c=isr.read();
            }
        } catch (IOException e) {
            throw new GenericSearchException(e.toString());
        }
        return docText;
    }
    
    /**
     * MIH: Added for MS-Word Support
     * 
     * @throws GenericSearchException.
     */
    private StringBuffer getTextFromDOC(byte[] doc)
            throws GenericSearchException {
        InputStream in = null;
        WordExtractor wd = null;
        try {
            in = new ByteArrayInputStream(doc);
            wd = new WordExtractor(in);
            StringBuffer buffer = new StringBuffer(wd.getText().trim());
            for (int c = 0; c < buffer.length(); c++) {
                if (buffer.charAt(c) <= '\u001F'
                        || buffer.charAt(c) == '\u201C'
                        || buffer.charAt(c) == '\u201D') {
                    buffer.setCharAt(c, ' ');
                }
            }
            return buffer;
        } catch (Exception e) {
            throw new GenericSearchException("Cannot parse Word document", e);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                }
                in = null;
            }
            wd = null;
        }
    }

    /**
     * 
     * 
     * @throws GenericSearchException.
     */
    private StringBuffer getTextFromPDF(byte[] doc)
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
    
    private void closeCOSDocument(COSDocument cosDoc) {
        if (cosDoc != null) {
            try {
                cosDoc.close();
            }
            catch (IOException e) {
            }
        }
    }
    
    private StringBuffer getTextFromPDFWithPdfBox(byte[] doc)
    throws GenericSearchException  {
        StringBuffer docText = new StringBuffer();
        COSDocument cosDoc = null;
        String password = "";
        boolean errorFlag = Boolean.parseBoolean(
                Config.getCurrentConfig().getIgnoreTextExtractionErrors());
        try {
            cosDoc = parseDocument(new ByteArrayInputStream(doc));
        } catch (IOException e) {
            closeCOSDocument(cosDoc);
            if (errorFlag) {
            	logger.warn(e);
                return new StringBuffer("textfrompdffilenotextractable");
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
            	logger.warn(e);
                return new StringBuffer("textfrompdffilenotextractable");
            } else {
                throw new GenericSearchException("Cannot decrypt PDF document",
                        e);
            }
        } catch (InvalidPasswordException e) {
            closeCOSDocument(cosDoc);
            if (errorFlag) {
            	logger.warn(e);
                return new StringBuffer("textfrompdffilenotextractable");
            } else {
                throw new GenericSearchException("Cannot decrypt PDF document",
                        e);
            }
        } catch (IOException e) {
            closeCOSDocument(cosDoc);
            if (errorFlag) {
            	logger.warn(e);
                return new StringBuffer("textfrompdffilenotextractable");
            } else {
                throw new GenericSearchException("Cannot decrypt PDF document",
                        e);
            }
        }

        // extract PDF document's textual content
        try {
            PDFTextStripper stripper = new PDFTextStripper();
            docText = new StringBuffer(stripper.getText(new PDDocument(cosDoc)));
        } catch (Throwable e) {
            closeCOSDocument(cosDoc);
            if (errorFlag) {
            	logger.warn(e);
                return new StringBuffer("textfrompdffilenotextractable");
            } else {
                throw new GenericSearchException(
                        "Cannot parse PDF document", e);
            }
        }
        closeCOSDocument(cosDoc);
        return docText;
    }

    private StringBuffer getTextFromPDFWithItext(byte[] doc) throws GenericSearchException {
        StringBuffer docText = new StringBuffer();
        boolean errorFlag = Boolean.parseBoolean(
            Config.getCurrentConfig().getIgnoreTextExtractionErrors());
        try {
//            docText.append(" ");
//            PdfReader reader = new PdfReader(doc);
//            for (int i = 1; i <= reader.getNumberOfPages(); i++) {
//                docText.append(PdfTextExtractor.getTextFromPage(reader, i)).append(" ");
//            }
        	docText.append(" ");
        } catch (Throwable e) {
            if (errorFlag) {
            	logger.warn(e);
                return new StringBuffer("textfrompdffilenotextractable");
            } else {
                throw new GenericSearchException(
                        "Cannot parse PDF document", e);
            }
        }
        return docText;
    }

    private StringBuffer getTextFromPDFWithExternalTool(byte[] doc) throws GenericSearchException {
        boolean errorFlag = Boolean.parseBoolean(
            Config.getCurrentConfig().getIgnoreTextExtractionErrors());
        StringBuffer textBuffer = new StringBuffer("");
        
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
            String catalinaHome = System.getProperty("catalina.home");
            if (catalinaHome != null) {
                catalinaHome += "/";
            } else {
                catalinaHome = "";
            }
            inputFileName = catalinaHome + currMillies + ".pdf";
            outputFileName = catalinaHome + currMillies + ".txt";

            //write pdf-bytes to file
            File f = new File(inputFileName);
            fop = new FileOutputStream(f);
            fop.write(doc);
            fop.flush();
            fop.close();
            
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
                    new InputStreamReader(p.getInputStream()));
            errIn = new BufferedReader(
                    new InputStreamReader(p.getErrorStream()));
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
                    new InputStreamReader(fileIn, "UTF-8"));
            String str = new String("");
            while ((str = in.readLine()) != null) {
                textBuffer.append(str).append(" ");
            }
            fileIn.close();
        } catch (Throwable e) {
            if (errorFlag) {
            	logger.warn(e);
                return new StringBuffer("textfrompdffilenotextractable");
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
    
    private void closePDDocument(PDDocument pdDoc) {
        if (pdDoc != null) {
            try {
            	pdDoc.close();
            }
            catch (IOException e) {
            }
        }
    }
    private void checkGibberish(final StringBuffer buffer) throws Exception {
        int j = 0;
        for (int i = 0; i < buffer.length(); i++) {
            if (j > 100) {
                throw new Exception("gibberish");
            }
            String hex = charToHex(buffer.charAt(i));
            int intValue = Integer.parseInt(hex, 16);
            if (intValue < 9) {
              j++;
            }
        }
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

     private String charToHex(char c) {
        // Returns hex String representation of char c
        byte hi = (byte) (c >>> 8);
        byte lo = (byte) (c & 0xff);
        return byteToHex(hi) + byteToHex(lo);
     }

     public static void main(String[] args) {
    }

}
