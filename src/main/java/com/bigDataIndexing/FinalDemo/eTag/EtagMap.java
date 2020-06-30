package com.bigDataIndexing.FinalDemo.eTag;

/* dhaval created on 2/8/20 inside the package - com.bigDataIndexing.FinalDemo.eTag */

import java.util.HashMap;
import java.util.Map;

public class EtagMap {
    private static Map<String, String> etags;

    private EtagMap() {
    }

    public static Map<String, String> getEtags() {
        if (etags == null) {
            etags = new HashMap<>();
        }
        return etags;
    }

    public static void setEtags(Map<String, String> etags) {
        EtagMap.etags = etags;
    }
}
