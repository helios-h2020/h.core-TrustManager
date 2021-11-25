package eu.h2020.helios_social.core.trustmanager;

import eu.h2020.helios_social.core.contextualegonetwork.Context;
import eu.h2020.helios_social.core.contextualegonetwork.Node;
import eu.h2020.helios_social.core.contextualegonetwork.Edge;
import eu.h2020.helios_social.modules.neurobehaviour.NeurobehaviourListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

/**
 * This class implements the context-related threads, and contains the methods needed to compute the trust scores.
 * Every time a context is created (or becomes active after a period of inactivity), the related thread is
 * created (or becomes active). The manager communicates with the threads through variable conditions, that are
 * exploited to put a thread in a wait state when the related context is not active anymore or, otherwise,
 * to notify a thread that the related context has become active. For the period of time in which the context is
 * active, each amount of time (which is specified by the parameter deltaT) the related thread computes a trust score
 * for all the alters in that context.
 *
 * @author Barbara Guidi (guidi@di.unipi.it)
 * @author Laura Ricci (ricci@di.unipi.it)
 * @author Andrea Michienzi (andrea.michienzi@di.unipi.it)
 * @author Giulia Fois (g.fois5@studenti.unipi.it)
 * @author Fabrizio Baiardi (f.baiardi@unipi.it)
 */
public class ContextTrustUpdater extends Thread {

    /**
     * Class that acts as a wrapper for the trust score, to be
     * stored on the edges of the Contextual Ego Network
     */
    class ComputedTrustValue {
        /**
         * Trust score to be stored on the edge
         */
        private float trustVal;

        /**
         * Constructor method
         */
        public ComputedTrustValue() {
        }

        /**
         * @param tv The trust score to be stored
         */
        public void putTrustVal(float tv) {
            trustVal = tv;
        }
    }

    /**
     * Reference to the context this thread is related to
     */
    private Context context;
    /**
     * Structure that maps each alter to its latest computed trust value
     */
    private HashMap<Node, Float> trustMap;
    /**
     * Reference to the NeuroBehavioural Listener, which is exploited to
     * call the NeuroBehavioural Module whenever the Sentiment Analysis
     * score has to be computed
     */
    private NeurobehaviourListener nBL;
    /**
     * Concurrency objects, used by the Trust Manager to communicate with each
     * thread in case of context state switch. The lock provides a mutex
     * mechanism for the shared variable <i>active</i>
     */
    private Lock contextLock;
    private Condition contextCondVar;
    /**
     * Boolean variable that is true if the related context is active
     * and false otherwise
     */
    private boolean active;
    /**
     * Structure that translates the Sentiment Analysis classes given by the
     * NeuroBehavioural Module into float scores, that are put together
     * to compute the trust-related Sentiment Analysis score
     */
    private Map<String, Float> emotionHashMap;
    /**
     * Boolean variable that becomes true if and only if the related context
     * is removed from the Contextual Ego Network, and therefore this thread
     * ends its life cycle
     */
    private boolean terminate = false;

    /**
     * Constructor method. It creates a thread instance related to the
     * context that is passed as parameter
     * @param c The context this thread is related to
     */
    public ContextTrustUpdater(Context c) {
        emotionHashMap = new HashMap<>();
        emotionHashMap.put("Attention_High", 1.f);
        emotionHashMap.put("Attention_Medium", 0.66f);
        emotionHashMap.put("Attention_Low", 0.33f);
        emotionHashMap.put("PositivePositive", 1.f);
        emotionHashMap.put("PositiveNegative", 0.75f);
        emotionHashMap.put("NegativeNegative", 0.5f);
        emotionHashMap.put("NegativePositive", 0.25f);

        context = c;
        nBL = new NeurobehaviourListener();
        contextLock = new ReentrantLock();
        contextCondVar = contextLock.newCondition();
        active = true;
    }

    /**
     * Method that is executed when the Trust Manager calls the <i>start</i> method.
     * It is the main flow (??) of the thread, that computes trust scores for each
     * node in the related context in the latter's periods of activity.
     */
    public void run() {
        trustMap = new HashMap<>();
        ArrayList<Node> alters = context.getNodes();
        for(Node n: alters) {
            if(!n.equals(TrustManager.ego)) trustMap.put(n, initializeTrust(n));
        }

        while(!terminate) {
            contextLock.lock();
            if(!active) {
                try { contextCondVar.await(); }
                catch (InterruptedException e) { contextLock.unlock(); }
            }
            updateTrust();
            contextLock.unlock();
            try { Thread.sleep(TrustManager.deltaT); }
            catch (InterruptedException e) { e.printStackTrace(); }
        }
    }

    /**
     * Method that is invoked every deltaT seconds to update the trust scores towards
     * all the nodes in the context. The trust scores are then saved on the Contextual Ego
     * Network.
     */
    protected void updateTrust() {

        ArrayList<Node> alters = context.getNodes();
        for(Node n: alters) {
            if(!n.equals(TrustManager.ego)) {
                float trustScore = 0.f;
                if(trustMap.get(n) == null) {
                    trustScore = initializeTrust(n);
                    trustMap.put(n, trustScore);
                }
                else {
                    trustScore = computeTrust(n);
                    trustMap.replace(n, trustScore);
                }

                //Store of the trust score on the Contextual Ego Network
                ComputedTrustValue ctv = context.getEdge(TrustManager.ego, n).getOrCreateInstance(ComputedTrustValue.class);
                ctv.putTrustVal(trustScore);
            }
        }

    }

    /**
     * This method is called whenever a new alter is added to the context this thread
     * refers to. It computes and stores the initial trust value towards the alter.
     * @param alter The alter that has been newly added to the context this thread refers to
     */
    protected void newAlterTrust(Node alter) {
        float trustScore = 0.f;
        if(trustMap.get(alter) != null) {
            TrustManager.eh.error(new IllegalArgumentException());
            return;
        }
        else {
            trustScore = initializeTrust(alter);
            trustMap.put(alter, trustScore);
        }
        //Store of the trust score on the Contextual Ego Network
        ComputedTrustValue ctv = context.getEdge(TrustManager.ego, alter).getOrCreateInstance(ComputedTrustValue.class);
        ctv.putTrustVal(trustScore);
    }

    /**
     * This method computes the initial trust score towards an alter in the context.
     * @param alter The alter towards which the trust score is initialized
     * @return A float value, that is the initial trust score
     */
    protected float initializeTrust(Node alter) {
        return TrustManager.ps_init_w * profileSimilarity(alter) + TrustManager.cf_init_w * commonFriends(alter)
                + TrustManager.pr_init_w * proximity(alter);
    }

    /**
     * This method computes the trust score, at a certain time, towards an alter in the context.
     * @param alter The alter towards which the trust score is computed
     * @return A float value, that is the trust score, or 0 if the alter doesn't exist in this context
     *         or if <i>alter</i> is null
     */
    private float computeTrust(Node alter) {
        if(alter == null) {
            TrustManager.eh.error(new NullPointerException());
            return 0.f;
        }
        else if(isInContext(alter))
            return  TrustManager.cf_w * commonFriends(alter) +
                TrustManager.sa_w * sentimentAnalysis(alter) + TrustManager.pr_w * proximity(alter);
        else
            return 0.f;
    }

    /**
     * This method computes the Profile Similarity score, that is one of the parameters that
     * make up the initial trust score.
     * @param alter The alter towards which the Profile Similarity score is computed
     * @return A float, that is the Profile Similarity score
     */
    private float profileSimilarity(Node alter) {
        return 0.f;
    }

    /**
     * This method computes the Common Friends score, that is one of the parameters that
     * make up the trust score.
     * @param alter The alter towards which the Common Friends score is computed
     * @return A float, that is the Common Friends score
     */
    private float commonFriends(Node alter) {

        ArrayList<Node> egoFriends = context.getNodes();
        Stream<Edge> alterInEdges = context.getInEdges(alter);
        Stream<Edge> alterOutEdges = context.getOutEdges(alter);
        ArrayList<Node> alterInFriends = new ArrayList<>();
        ArrayList<Node> alterOutFriends = new ArrayList<>();
        alterInEdges.forEach(e -> {alterInFriends.add(e.getSrc());});
        alterOutEdges.forEach(e -> {alterOutFriends.add(e.getDst());});

        int commonFriends = 0;
        for(Node n: egoFriends) {
            if(!(n.equals(TrustManager.ego) || n.equals(alter))) {
                if(alterInFriends.contains(n) || alterOutFriends.contains(n)) commonFriends ++;
            }
        }
        return (float) commonFriends / egoFriends.size();
    }

    /**
     * This method computes the Sentiment Analysis score, that is one of the parameters that
     * make up the trust score. This score is computed by combining the emotional scores
     * returned by the NeuroBehavioural Module with respect to the alter in this specific context.
     * @param alter The alter towards which the Sentiment Analysis score is computed
     * @return A float, that is the Sentiment Analysis score
     */
    private float sentimentAnalysis(Node alter) {

        String[][] emotionalValues = nBL.egoAlterTrust(alter.getId());
        String[] contextEmotionalValues = new String[]{};

        //The values related to this specific context are taken into consideration
        for(int sIdx = 0; sIdx < emotionalValues.length; sIdx++) {
            if(emotionalValues[sIdx].equals(context.getSerializationId()))
                contextEmotionalValues = emotionalValues[sIdx];
        }

        String valence = contextEmotionalValues[0];
        String arousal = contextEmotionalValues[1];
        String attention = contextEmotionalValues[2];

        float valence_arousal_combination = emotionHashMap.get(valence + arousal);
        float attention_val = emotionHashMap.get("Attention_" + attention);

        //Return a combination of the values
        return valence_arousal_combination * attention_val;
    }

    /**
     * This method computes the Proximity score, that is one of the parameters that
     * make up the trust score.
     * @param alter The alter towards which the Proximity score is computed
     * @return A float, that is the Proximity score, or 0 if the alter doesn't exist in this context
     */
    private float proximity(Node alter) {
        return 0.f;
    }

    /**
     * This method returns the latest computed trust score towards an alter in
     * this specific context.
     * @param  alter The alter towards which the trust score is requested
     * @return The last trust value that has been computed for an alter, or 0 if the alter doesn't exist
     *         in this context or if alter is null
     */
    public float getTrust(Node alter) {
        if(alter == null) {
            TrustManager.eh.error(new NullPointerException());
            return 0.f;
        }
        if(isInContext(alter))
            return trustMap.get(alter);
        else
            return 0.f;
    }

    /**
     * This method checks if a trust score for an alter exists in relation to this
     * specific context.
     * @param alter The alter for which the check is made
     * @return true if such trust score exists, false otherwise
     */
    private boolean isInContext(Node alter) {
        if(trustMap.get(alter) == null) {
            TrustManager.eh.error(new IllegalArgumentException());
            return false;
        }
        return true;
    }

    /**
     * @return The lock variable associated to this thread
     */
    protected Lock getLock() {
        return contextLock;
    }

    /**
     * @return The condition variable associated to this thread
     */
    protected Condition getCondVar() {
        return contextCondVar;
    }

    /**
     * This method notifies this thread that the corresponding context's state
     * has turned to active.
     */
    protected void setActive() {
        active = true;
    }

    /**
     * This method notifies this thread that the corresponding context's state
     * has turned to inactive.
     */
    protected void setInactive() {
        active = false;
    }

    /**
     * This method sets the termination variable to true because the context that
     * corresponds to this thread has been removed from the Contextual Ego Network.
     * This thread will stop its life flow.
     */
    protected void terminate() { terminate = true; }
}
