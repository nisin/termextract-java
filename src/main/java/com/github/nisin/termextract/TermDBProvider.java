package com.github.nisin.termextract;

import com.github.nisin.termextract.model.Comb;
import com.github.nisin.termextract.model.Df;
import com.github.nisin.termextract.model.Stat;

import java.util.Map;

/**
 *
 * Created by Shoichi on 2014/01/30.
 */
public interface TermDBProvider {
    Map<String,Stat> getStatMap();
    Map<String,Comb> getCombMap();
    Map<String,Df> getDocumentFrequencyMap();
}
