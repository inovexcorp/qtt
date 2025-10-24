package com.inovexcorp.queryservice;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.*;

@RunWith(MockitoJUnitRunner.class)
public class TestRdfResultsJsonifier {

    private static final File RDF_FILE = new File("src/test/resources/test.rdf");

    @Mock
    private Exchange exch;
    @Mock
    private Message message;
    @Mock
    private RDFSerializerConfig config;
    @Captor
    private ArgumentCaptor<String> output;


    @Before
    public void initMock() throws FileNotFoundException {
        when(exch.getMessage()).thenReturn(message);
        when(message.getBody(InputStream.class))
                .thenReturn(new FileInputStream(RDF_FILE));
    }

    @Before
    public void initConfig() {
        when(config.baseUri()).thenReturn("http://inovexcorp.com/query-service/");
        when(config.jsonLdMode()).thenReturn("COMPACT");
        when(config.optimize()).thenReturn(true);
        when(config.useNativeTypes()).thenReturn(true);
        when(config.compactArrays()).thenReturn(true);
    }

    @Test
    public void testSimple() throws Exception {
        RdfResultsJsonifier jsonifier = new RdfResultsJsonifier();
        jsonifier.initialize(config);
        jsonifier.process(exch);
        verify(message).setBody(output.capture());
        String data = output.getValue();
        Assert.assertNotNull(data);
        Assert.assertFalse(data.isEmpty());
        Model m = Rio.parse(new StringReader(data), RDFFormat.JSONLD);
        Assert.assertNotNull(m);
        Assert.assertFalse(m.isEmpty());
        jsonifier.stop();
    }
}
