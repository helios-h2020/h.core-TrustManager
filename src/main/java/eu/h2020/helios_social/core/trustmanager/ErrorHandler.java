package eu.h2020.helios_social.core.trustmanager;

import java.util.logging.Logger;

/**
 * This class implements some error handling methods, that differentiate the first testing
 * phases from the normal running state of the module.
 *
 * @author Barbara Guidi (guidi@di.unipi.it)
 * @author Laura Ricci (ricci@di.unipi.it)
 * @author Andrea Michienzi (andrea.michienzi@di.unipi.it)
 * @author Giulia Fois (g.fois5@studenti.unipi.it)
 */
public class ErrorHandler {

    /**
     * This variable is set to true while the module is being tested, in order to be promptly
     * notified if some anomalous behaviour takes place
     */
    private boolean development = true;
    /**
     * Object used for logging purposes
     */
    private static Logger logger;

    /**
     * Constructor method
     */
    public ErrorHandler() {
        logger = Logger.getGlobal();
    }

    /**
     * This method is called whenever an exception would normally need to be thrown. If the module
     * is in testing phase, the exception is actually thrown. Otherwise, the anomaly is logged
     * and the module keeps running.
     * @param ex Exception that has to be thrown/logged
     */
    protected void error(Exception ex) {
        if(development) {
            ex.printStackTrace();
            throw new RuntimeException(ex);
        }
        else {
            logger.warning(ex.getLocalizedMessage());
        }
    }
}