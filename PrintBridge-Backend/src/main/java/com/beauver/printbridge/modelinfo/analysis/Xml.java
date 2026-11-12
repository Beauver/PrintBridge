package com.beauver.printbridge.modelinfo.analysis;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;

/** Streaming XML reading with DTDs and external entities switched off (XXE protection). */
final class Xml {

    private static final XMLInputFactory FACTORY = createFactory();

    private Xml() {
    }

    private static XMLInputFactory createFactory() {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.IS_COALESCING, false);
        return factory;
    }

    static XMLStreamReader reader(InputStream in) throws XMLStreamException {
        return FACTORY.createXMLStreamReader(in);
    }

    /** Attribute by local name, ignoring its namespace (3MF uses prefixes like {@code p:path}). */
    static String attribute(XMLStreamReader reader, String localName) {
        for (int i = 0; i < reader.getAttributeCount(); i++) {
            if (reader.getAttributeLocalName(i).equals(localName)) {
                return reader.getAttributeValue(i);
            }
        }
        return null;
    }

    static int intAttribute(XMLStreamReader reader, String localName) throws ModelAnalysisException {
        String value = attribute(reader, localName);
        try {
            return Integer.parseInt(value == null ? "" : value.trim());
        } catch (NumberFormatException e) {
            throw new ModelAnalysisException("Invalid number in attribute " + localName, e);
        }
    }

    static double doubleAttribute(XMLStreamReader reader, String localName) throws ModelAnalysisException {
        String value = attribute(reader, localName);
        try {
            double number = Double.parseDouble(value == null ? "" : value.trim());
            if (!Double.isFinite(number)) {
                throw new ModelAnalysisException("Invalid number in attribute " + localName);
            }
            return number;
        } catch (NumberFormatException e) {
            throw new ModelAnalysisException("Invalid number in attribute " + localName, e);
        }
    }

    /**
     * The exception to report for a parse error. A limit hit while reading (see
     * {@link ReadBudget}) reaches us wrapped by the parser; that one is the real reason.
     */
    static ModelAnalysisException failure(String message, Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof ModelAnalysisException analysisException) {
                return analysisException;
            }
        }
        return new ModelAnalysisException(message, error);
    }

    static void close(XMLStreamReader reader) {
        try {
            reader.close();
        } catch (XMLStreamException e) {
            // Nothing left to read; the underlying stream is closed by its owner.
        }
    }
}
