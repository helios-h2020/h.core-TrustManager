package eu.h2020.helios_social.core.trustmanager;

import eu.h2020.helios_social.core.contextualegonetwork.ContextualEgoNetwork;
import eu.h2020.helios_social.core.contextualegonetwork.Context;
import eu.h2020.helios_social.core.contextualegonetwork.Node;

import java.util.HashMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

public class TrustManager {

    private ContextualEgoNetwork cen;

    protected static Node ego;
    protected static float ps_init_w;
    protected static float cf_init_w;
    protected static float ps_w;
    protected static float cf_w;
    protected static float sa_w;
    protected static float pr_w;

    protected static int deltaT;

    private HashMap<Context, ContextTrustUpdater> contextThreads;

    //The module has to be called with a reference to the CEN
    public TrustManager(ContextualEgoNetwork c, int deltaT) {
        this.cen = c;
        ego = cen.getEgo();
        ps_init_w = 0.5f;
        cf_init_w = 0.5f;
        ps_w = 0.2f;
        cf_w = 0.2f;
        sa_w = 0.5f;
        pr_w = 0.1f;

        this.deltaT = deltaT;
        contextThreads = new HashMap<>();
    }

    public TrustManager(ContextualEgoNetwork c, int deltaT, HashMap<String, Float> modelWeights) {
        this(c, deltaT);
        ps_init_w = modelWeights.get("ProfileSimilarityInit");
        ps_w = modelWeights.get("ProfileSimilarity");
        cf_init_w = modelWeights.get("CommonFriendsInit");
        cf_w = modelWeights.get("CommonFriends");
        sa_w = modelWeights.get("SentimentAnalysis");
        pr_w = modelWeights.get("Proximity");
    }

    //Assumption: contexts are added once they are active
    public void startModule() {

        for(Context c: cen.getContexts()) {
            if(c.isLoaded()) contextThreads.put(c, new ContextTrustUpdater(c));
        }

        for(Context c: contextThreads.keySet()) {
            contextThreads.get(c).start();
        }

    }


    public void newContext(Context c) {

        ContextTrustUpdater contThread = new ContextTrustUpdater(c);
        contextThreads.put(c, contThread);
        contThread.start();

    }

    public void activateContext(Context c) {
        ContextTrustUpdater contThread = contextThreads.get(c);
        Lock contLock = contThread.getLock();
        Condition condVar = contThread.getCondVar();
        contLock.lock();
        contThread.setActive();
        condVar.signal();
        contLock.unlock();

    }

    public void deactivateContext(Context c) {
        ContextTrustUpdater contThread = contextThreads.get(c);
        Lock contLock = contThread.getLock();
        contLock.lock();
        contThread.setInactive();
        contLock.unlock();

        //call for the last update
        contThread.updateTrust();
    }

    public float getTrust(Context c, Node alter) {
       return contextThreads.get(c).getTrust(alter);
    }


}
