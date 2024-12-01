package org.upb_sw_factChecking;

import org.slf4j.Logger;

public class App {

    public static final Logger logger = org.slf4j.LoggerFactory.getLogger(App.class);

    public String getGreeting() {
        return "Hello World!";
    }

    public static void main(String[] args) {
        System.out.println(new App().getGreeting());
        logger.info("nochmals hallo");
    }

}
