package com.github.nisin.termextract.model;

import java.io.Serializable;

/**
 * 出現した２語の連接とその頻度
 * Created by Shoichi on 2014/01/30.
 */
public class Comb implements Serializable {
    private static final long serialVersionUID = 2754193820752299262L;
    /** 特長語 */
    public String noun;
    /** 頻度 */
    public Integer frq;
}
