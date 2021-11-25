package eu.h2020.helios_social.core.trustmanager;

import eu.h2020.helios_social.core.contextualegonetwork.Context;
import eu.h2020.helios_social.core.contextualegonetwork.ContextualEgoNetworkListener;
import eu.h2020.helios_social.core.contextualegonetwork.Node;

/**
 * This class implements the ContextualEgoNetwork Listener, that allows the Trust Manager to
 * react to events happening in the CEN (such as the activation/deactivation of a context, the
 * insertion of a new node in a context and so on) with specific callbacks.
 *
 * @author Barbara Guidi (guidi@di.unipi.it)
 * @author Laura Ricci (ricci@di.unipi.it)
 * @author Andrea Michienzi (andrea.michienzi@di.unipi.it)
 * @author Giulia Fois (g.fois5@studenti.unipi.it)
 * @author Fabrizio Baiardi (f.baiardi@unipi.it)
 */
public class TrustCENListener implements ContextualEgoNetworkListener {
    /**
     * Reference to the TrustManager
     */
    TrustManager trustManager;

    /**
     * Constructor method
     * @param tm A reference to the TrustManager object
     */
    public TrustCENListener(TrustManager tm) {
        trustManager = tm;
    }

    /**
     * Method that is triggered whenever a new context is added to the Contextual Ego Network. It triggers
     * the corresponding method of the TrustManager.
     * @param context The new context that is added to the CEN
     */
    public void onCreateContext(Context context) {
        trustManager.newContext(context);
    }

    /**
     * Method that is triggered whenever a context's status is switched from active to inactive
     * in the Contextual Ego Network. It triggers the corresponding method of the TrustManager.
     * @param context The context that has become active
     */
    public void onLoadContext(Context context) {
        trustManager.activateContext(context);
    }

    /**
     * Method that is triggered whenever a context's status is switched from inactive to active
     * in the Contextual Ego Network. It triggers the corresponding method of the TrustManager.
     * @param context The context that has become inactive
     */
    public void onSaveContext(Context context) {
        trustManager.deactivateContext(context);
    }

    /**
     * Method that is triggered whenever a context is removed from the Contextual Ego Network.
     * It triggers the corresponding method of the TrustManager.
     * @param context The context that has been removed
     */
    public void onRemoveContext(Context context) {
        trustManager.removeContext(context);
    }

    /**
     * Method that is triggered whenever a node is added to a context in the Contextual Ego
     * Network. It triggers the corresponding method of the TrustManager.
     * @param context The context in which the node has been added
     * @param node The node that is added to the context in the CEN
     */
    public void onAddNode(Context context, Node node) {
        trustManager.addAlterToContext(node, context);
    }


}
