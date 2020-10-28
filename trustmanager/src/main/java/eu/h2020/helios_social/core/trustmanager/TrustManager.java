package eu.h2020.helios_social.core.trustmanager;

import eu.h2020.helios_social.core.contextualegonetwork.ContextualEgoNetwork;
import eu.h2020.helios_social.core.contextualegonetwork.Context;
import eu.h2020.helios_social.core.contextualegonetwork.CrossModuleComponent;
import eu.h2020.helios_social.core.contextualegonetwork.Node;

import java.util.HashMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

/**
 * This is the main class of the Trust Module. After being instantiated, it will automatically
 * start computing trust values towards the nodes in the active contexts.
 * This class contains all the parameters that are needed to compute the trust values, namely the weights
 * of the module (that can be set to default or chosen as desired by passing them to the constructor) and the time interval
 * that elapses between trust computations.
 *
 * @author Barbara Guidi (guidi@di.unipi.it)
 * @author Laura Ricci (ricci@di.unipi.it)
 * @author Andrea Michienzi (andrea.michienzi@di.unipi.it)
 * @author Giulia Fois (g.fois5@studenti.unipi.it)
 */
public class TrustManager {

    /**
     * Reference to the Contextual Ego Network, from which the Trust Manager retrieves the social information
     * and on which the trust information is stored
     */
    private ContextualEgoNetwork cen;
    /**
     * Reference to the ego node
     */
    protected static Node ego;
    /**
     * Initial weight for the profile similarity trust parameter
     */
    protected static float ps_init_w;
    /**
     * Initial weight for the proximity trust parameter
     */
    protected static float pr_init_w;
    /**
     * Initial weight for the common friends trust parameter
     */
    protected static float cf_init_w;
    /**
     * Weight for the common friends trust parameter
     */
    protected static float cf_w;
    /**
     * Weight for the sentiment analysis trust parameter
     */
    protected static float sa_w;
    /**
     * Weight for the proximity trust parameter
     */
    protected static float pr_w;
    /**
     * Time interval that elapses between trust computations. It is expressed
     * in seconds.
     */
    protected static int deltaT;
    /**
     * Structure that maps each context to the thread that handles the computation of
     * trust values for the nodes in it
     */
    private HashMap<Context, ContextTrustUpdater> contextThreads;
    /**
     * Object used for error handling
     */
    protected static ErrorHandler eh;
    /**
     * Object used for storing the trust values on the Contextual Ego Network
     */
    protected static CrossModuleComponent cmc;
    /**
     * Object used for adding callbacks to events that happen in the Contextual Ego Network
     */
    private TrustCENListener tcl;

    /**
     * Constructor method. It creates an instance of Trust Manager. By calling this constructor,
     * the weights of the trust model parameters are set to default.
     * @param c A reference to the Contextual Ego Network module
     * @param deltaT The time that has to elapse between trust computations
     */
    private TrustManager(ContextualEgoNetwork c, int deltaT) {
        this.cen = c;
        ego = cen.getEgo();
        eh = new ErrorHandler();
        tcl = new TrustCENListener(this);
        cen.addListener(tcl);

        ps_init_w = 0.2f;
        pr_init_w = 0.1f;
        cf_init_w = 0.7f;
        cf_w = 0.3f;
        sa_w = 0.6f;
        pr_w = 0.1f;

        this.deltaT = deltaT;
        contextThreads = new HashMap<>();
    }

    /**
     * Constructor method. It creates an instance of Trust manager with chosen model parameter weights.
     * The weights that are not specified are set to default.
     * @param c A reference to the Contextual Ego Network module
     * @param deltaT The time that has to elapse between trust computations
     * @param modelWeights A map containing chosen values for the model parameter weights. To set the
     *                     parameter weights, insert the following keys in the map:
     *                     <ul>
     *                      <li><b>ProfileSimilarityInit</b> to set the initial weight for the profile
     *                              similarity parameter. Since this parameter is optional, one can decide
     *                              not to take it into account in the computation by assigning it a weight of 0.</li>
     *                      <li><b>CommonFriendsInit</b> to set the initial weight for the common
     *                                    friends parameter</li>
     *                      <li><b>CommonFriends</b> to set the weight for the common friends parameter</li>
     *                     <li><b>ProximityInit</b> to set the initial weight for the proximity parameter</li>
     *                     <li><b>Proximity</b> to set the weight for the proximity parameter</li>
     *                      <li><b>SentimentAnalysis</b> to set the weight for the sentiment
     *                          analysis parameter</li>
     *                      </ul>
     */
    public TrustManager(ContextualEgoNetwork c, int deltaT, HashMap<String, Float> modelWeights) {
        this(c, deltaT);

        if(modelWeights.get("ProfileSimilarityInit") != null)
            ps_init_w = modelWeights.get("ProfileSimilarityInit");
        if(modelWeights.get("CommonFriendsInit") != null)
            cf_init_w = modelWeights.get("CommonFriendsInit");
        if(modelWeights.get("ProximityInit") != null)
            pr_init_w = modelWeights.get("ProximityInit");
        if(modelWeights.get("CommonFriends") != null)
            cf_w = modelWeights.get("CommonFriends");
        if(modelWeights.get("SentimentAnalysis") != null)
            sa_w = modelWeights.get("SentimentAnalysis");
        if(modelWeights.get("Proximity") != null)
            pr_w = modelWeights.get("Proximity");

        if(ps_init_w == 0) {
            float gap = 1 - (cf_init_w + pr_init_w);
            cf_init_w += gap/2;
            pr_init_w += gap/2;
        }
    }

    /**
     * Starts the trust handling threads for the currently active contexts.
     */
    public void startModule() {

        for(Context c: cen.getContexts()) {
            if(c.isLoaded()) contextThreads.put(c, new ContextTrustUpdater(c));
        }

        for(Context c: contextThreads.keySet()) {
            contextThreads.get(c).start();
        }

    }

    /**
     * Adds a new context to the Trust Manager. The just added context is supposed to be active
     * when this method is called.
     * @param c A reference to the newly created context
     */
    public void newContext(Context c) {

        ContextTrustUpdater contThread = new ContextTrustUpdater(c);
        contextThreads.put(c, contThread);
        contThread.start();

    }

    /**
     * This method has to be called when a context's status is switched to active. The related
     * trust handling thread is notified, in order to re-start computing trust values towards the nodes
     * within that context.
     * @param c The context whose status has been switched to active
     */
    public void activateContext(Context c) {
        ContextTrustUpdater contThread = contextThreads.get(c);
        Lock contLock = contThread.getLock();
        Condition condVar = contThread.getCondVar();
        contLock.lock();
        contThread.setActive();
        condVar.signal();
        contLock.unlock();

    }

    /**
     * This method has to be called when a context's status is switched to inactive. The related
     * trust handling thread is notified, and put to a waiting status through a condition variable.
     * @param c The context whose status has been switched to inactive
     */
    public void deactivateContext(Context c) {
        if(c == null) eh.error(new NullPointerException());
        else if(!contextThreads.containsKey(c)) eh.error(new IllegalArgumentException());
        else {
            ContextTrustUpdater contThread = contextThreads.get(c);
            Lock contLock = contThread.getLock();
            contLock.lock();
            contThread.setInactive();
            contLock.unlock();

            //Updates trust for the last time before the context becomes inactive,
            //in order to maintain consistent trust values
            contThread.updateTrust();
        }

    }

    /**
     * Gets the trust value related to an alter in a specific context. Trust isn't directly
     * recomputed; the value that is returned is the one saved by the thread (that is the one
     * that is present in the Contextual Ego Network) at a moment x that belongs to the interval
     * (t - deltaT, t) (where t represents the current time)
     * @param c The context within which the trust value towards the alter has to be computed
     * @param alter The alter towards which the trust value has to be computed
     * @return The trust value computed at the moment x
     */
    public float getTrust(Context c, Node alter) {
        if(c == null || alter == null) eh.error(new NullPointerException());
        else if(!contextThreads.containsKey(c)) eh.error(new IllegalArgumentException());
        return contextThreads.get(c).getTrust(alter);
    }

    /**
     * This method has to be called when an alter is added to a context. The thread related to
     * that context will compute an initial trust value towards the alter.
     * @param alter The alter that has been newly added to the context
     * @param c The context in which the alter has been added
     */
    public void addAlterToContext(Node alter, Context c) {
        if(c == null || alter == null) eh.error(new NullPointerException());
        else if(!contextThreads.containsKey(c)) eh.error(new IllegalArgumentException());
        else contextThreads.get(c).newAlterTrust(alter);
    }

    /**
     * This method has to be called when a context is removed from the Contextual Ego Network.
     * The thread related to that context is shut down by the Trust Manager.
     * @param c The context that has been removed from the Contextual Ego Network
     */
    public void removeContext(Context c) {
        if(c == null) eh.error(new NullPointerException());
        else if(!contextThreads.containsKey(c)) eh.error(new IllegalArgumentException());
        else {
            ContextTrustUpdater contThread = contextThreads.get(c);
            contThread.terminate();
            try {
                contThread.join();
            }
            catch(InterruptedException e) {
                eh.error(e);
            }

        }
    }

}
