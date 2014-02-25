package com.github.nisin.termextract.model;

import java.io.Serializable;

/**
 * 単名詞単位の連接語統計
 * Created by Shoichi on 2014/01/30.
 */
public class Stat implements Serializable {

    private static final long serialVersionUID = -1519579388676753543L;
    /** 特長語 */
    public String  noun;
    /** 単独前方連接 */
    public int uniq_pre;
    /** 計前方連接 */
    public int total_pre;
    /** 単独後方連接 */
    public int uniq_post;
    /** 計前方連接 */
    public int total_post;
}
