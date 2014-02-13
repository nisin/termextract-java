package com.github.nisin.termextract.model;

import java.io.Serializable;

/**
 * 出現した２語の連接とその頻度
 * Created by Shoichi on 2014/01/30.
 */
public class Comb implements Serializable {
    private static final long serialVersionUID = -3826279170058085412L;
    /** 特長語 */
    String noun;
    /** 頻度 */
    Integer frq;
}
