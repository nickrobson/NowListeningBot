package xyz.nickr.telegram.nowlistening.db.models;

import xyz.nickr.telegram.nowlistening.NowListening;

/**
 * @author Nick Robson
 */
public class NLModel {

    public String toJSON() {
        return NowListening.GSON.toJson(this);
    }

}
