package de.fzi.cep.sepa.client.container.rest;

import com.clarkparsia.empire.SupportsRdfId;
import com.clarkparsia.empire.annotation.InvalidRdfException;
import de.fzi.cep.sepa.client.container.init.EmbeddedModelSubmitter;
import de.fzi.cep.sepa.commons.Utils;
import de.fzi.cep.sepa.desc.declarer.*;
import de.fzi.cep.sepa.model.NamedSEPAElement;
import de.fzi.cep.sepa.model.impl.graph.SecDescription;
import de.fzi.cep.sepa.model.impl.graph.SepDescription;
import de.fzi.cep.sepa.model.impl.graph.SepaDescription;
import de.fzi.cep.sepa.transform.Transformer;
import org.openrdf.model.Graph;
import org.openrdf.rio.RDFHandlerException;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.List;

public abstract class Element<D extends Declarer> {
//    private Map<String, N> runningInstances;

    public Element() {
    }

    protected abstract List<D> getElementDeclarers();

    @GET
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public String getDescription(@PathParam("id") String elementId) {
        List<D> declarers = getElementDeclarers();
        return getJsonLd(declarers, elementId);
    }

    protected <T extends  Declarer> String getJsonLd(List<T> declarers, String id) {
        NamedSEPAElement elem = getById(declarers, id);
        return toJsonLd(elem);
    }

    protected <T extends  Declarer> T getDeclarerById(List<T> declarers, String id) {
        for (T declarer : declarers) {
            if (declarer.declareModel().getUri().equals(id)) {
                return declarer;
            }
        }
        return null;
    }


//    protected void addRunnihjjkngInstance(String id, N instance) {
//
//        runningInstances.put(id, instance);
//    }
//
//    protected N getRunningInstance(String id) {
//        return runningInstances.get(id);
//    }
//
//    protected void removeRunningInstance(String id) {
//        runningInstances.remove(id);
//    }

    protected <T extends  Declarer> NamedSEPAElement getById(List<T> declarers, String id) {
        NamedSEPAElement desc = null;
        for (Declarer declarer : declarers) {
            if (declarer.declareModel().getUri().equals(id)) {
                //TODO find a better solution to add the event streams to the SepDescription
                if (declarer instanceof SemanticEventProducerDeclarer) {
                    SepDescription secDesc = ((SemanticEventProducerDeclarer) declarer).declareModel();
                    List<EventStreamDeclarer> eventStreamDeclarers = ((SemanticEventProducerDeclarer) declarer).getEventStreams();
                    for (EventStreamDeclarer esd : eventStreamDeclarers) {
                        secDesc.addEventStream(esd.declareModel(secDesc));
                    }

                    desc = secDesc;
                } else {
                    desc = declarer.declareModel();
                }
            }
        }

        //TODO remove this and find a better solution
        if (desc != null) {
            String type = "";

            if (desc instanceof SepaDescription) {
                type = "sepa/";
            } else if (desc instanceof SepDescription) {
                type = "sep/";
            } else if (desc instanceof SecDescription) {
                type = "sec/";
            }

            String uri = EmbeddedModelSubmitter.getBaseUri()+ "sepa/" + desc.getUri();
            desc.setUri(uri);
            desc.setRdfId(new SupportsRdfId.URIKey(URI.create(uri)));
        }

        return desc;
    }

    protected String toJsonLd(NamedSEPAElement namedElement) {
        if (namedElement != null) {
            Graph rdfGraph;
            try {
                rdfGraph = Transformer.toJsonLd(namedElement);
                return Utils.asString(rdfGraph);
            } catch (IllegalAccessException | InvocationTargetException | ClassNotFoundException | InvalidRdfException | RDFHandlerException e) {
                e.printStackTrace();
            }
        }
        return "{}";
    }
}
