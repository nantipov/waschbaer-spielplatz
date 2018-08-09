package org.nantipov.waschbaer;

import org.nantipov.waschbaer.services.SpielplatzService;

public class Boot {

    public static void main(String[] args) {
        SpielplatzService.getInstance().play();
    }

}
