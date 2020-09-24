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

public class ContextTrustUpdater extends Thread {

    private Context context;
    private HashMap<Node, Float> trustMap;
    private NeurobehaviourListener nBL;
    private Lock contextLock;
    private Condition contextCondVar;
    private boolean active;
    private Map<String, Float> emotionHashMap;

    public ContextTrustUpdater(Context c) {
        emotionHashMap = new HashMap<String, Float>();
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

    public void run() {
        trustMap = new HashMap<>();
        ArrayList<Node> alters = context.getNodes();
        for(Node n: alters) {
            if(!n.equals(TrustManager.ego)) trustMap.put(n, initializeTrust(n));
        }

        while(true) {
            updateTrust();
            try { Thread.sleep(TrustManager.deltaT); }
            catch (InterruptedException e) { e.printStackTrace(); }
        }
    }

    public void updateTrust() {
        //new nodes may have been added
        contextLock.lock();
        if(!active) {
            try { contextCondVar.await(); }
            catch (InterruptedException e) { contextLock.unlock(); }
        }
        ArrayList<Node> alters = context.getNodes();
        for(Node n: alters) {
            if(!n.equals(TrustManager.ego)) {
                if(trustMap.get(n) == null) trustMap.put(n, initializeTrust(n));
                else trustMap.replace(n, computeTrust(n));

                /*UPDATE THE TRUST VALUE ON THE EDGE: THE CEN DOESN'T IMPLEMENT THIS METHOD*/
                //context.getEdge(TrustModule.ego, n).UPDATETRUST
            }
        }
        contextLock.unlock();
    }

    public float initializeTrust(Node alter) {
        return TrustManager.ps_init_w * profileSimilarity(alter) + TrustManager.cf_init_w * commonFriends(alter);
    }

    public float computeTrust(Node alter) {
        return  TrustManager.ps_w * profileSimilarity(alter) + TrustManager.cf_w * commonFriends(alter) +
                TrustManager.sa_w * sentimentAnalysis(alter) + TrustManager.pr_w * proximity(alter);
    }

    public float profileSimilarity(Node alter) {
        return 0.f;
    }

    public float commonFriends(Node alter) {

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

    //La chiamata deve passare anche il contesto

    public float sentimentAnalysis(Node alter) {

        String[][] emotionalValues = nBL.egoAlterTrust(alter.getId());
        //CICLO SUI CONTESTI, PRENDO LE CLASSI
        String[] contextEmotionalValues = new String[]{};
        for(int sIdx = 0; sIdx < emotionalValues.length; sIdx++) {
            if(emotionalValues[sIdx].equals(context.getSerializationId()))
                contextEmotionalValues = emotionalValues[sIdx];
        }


        String valence = contextEmotionalValues[0];
        String arousal = contextEmotionalValues[1];
        String attention = contextEmotionalValues[2];

        float valence_arousal_combination = emotionHashMap.get(valence + arousal);
        float attention_val = emotionHashMap.get("Attention_" + attention);

        //return a combination of the values
        return valence_arousal_combination * attention_val;
    }

    //chiamata al neuro-behavioural module

    public float proximity(Node alter) {
        return 0.f;
    }

    /**
     * @return The last trust value that has been computed for an alter
     */
    public float getTrust(Node alter) {
        return trustMap.get(alter);
    }

    public Lock getLock() {
        return contextLock;
    }

    public Condition getCondVar() {
        return contextCondVar;
    }

    public void setActive() {
        active = true;
    }

    public void setInactive() {
        active = false;
    }
}
