package com.github.nisin.termextract;

import com.github.nisin.termextract.model.Articulate;

import java.util.Map;

/**
 * Created by Shoichi on 2014/01/30.
 */
public interface TermDBProvider {
    Map<String,Articulate> getArticulateMap();
    void saveArticulateMap(Map<String,Articulate> map);
    Map<String,Integer> getCombMap();
    void saveCombMap(Map<String,Integer> map);
    Map<String,Integer> getDocumentFrequencyMap();
    void saveDocumentFrequencyMap(Map<String,Integer> map);
}
