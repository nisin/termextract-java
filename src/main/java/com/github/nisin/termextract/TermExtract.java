package com.github.nisin.termextract;

import com.github.nisin.termextract.model.Articulate;
import com.github.nisin.termextract.model.Term;
import com.google.common.base.*;
import com.google.common.collect.*;
import org.atilika.kuromoji.Token;
import org.atilika.kuromoji.Tokenizer;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/**
 *
 * Created by Shoichi on 2014/01/30.
 */
public class TermExtract {
    private static final int MAX_CMP_SIZE = 1024;
    TermDBProvider provider;
    Map<String,Articulate> articulates;
    Map<String,Integer> combs;
    Map<String,Integer> documentFrequency;
//    /** 文中の専門用語とその頻度 */
//    Map<String,Integer> cmp_noun_list = Maps.newHashMap();
    /**          # 重要度計算対象外にする語のリスト（配列） */
    Set<String> ignore_words    = Sets.newHashSet();
    Tokenizer tokenizer = Tokenizer.builder().build();

    /** LRの設定　0: LRなし 1: 延べ数 2: 異なり数 3: パープレキシティ */
    enum LR_METHOD { no_LR,LR_TOTAL,LR_UNIQ,PERPLEXITY }
    LR_METHOD lr_method = LR_METHOD.LR_TOTAL;
    /** # 文中の用語頻度を、0: 無効にする 1: 有効にする  2: TFにする */
    enum FRQ_METHOD { no_FRQ, SIMPLE_FRQ, TERM_FRQ }
    FRQ_METHOD frq_method = FRQ_METHOD.SIMPLE_FRQ;
    /** # 重要度計算での連接情報と文中の用語頻度のバランス */
    double average_rate         = 1.0d;
    /** # 重要度計算で学習機能結果の使用  */
    boolean stat_mode           = false;
    /** # 学習用DBにデータを蓄積するか  */
    boolean storage_mode        = false;
    /** # df 用DBにデータを蓄積するか  */
    boolean storage_df          = false;
    /** # 重要度計算にIDFを使用するか */
    boolean with_idf            = false;
    /** # 処理対象言語が膠着言語か */
    boolean agglutinative_lang  = true;

    private Predicate<String> NOUN_FILTER = new Predicate<String>() {
        Pattern ALL_SPACE = Pattern.compile("^\\s*$");
        @Override
        public boolean apply(String s) {
            return ! (ALL_SPACE.matcher(s).matches() || s.length() > MAX_CMP_SIZE);
        }
    };
    private static final Pattern ALL_DECIMAL = Pattern.compile("^[\\d,\\.]$");
    private Predicate<String> IGNORE_FILTER = new Predicate<String>() {
        @Override
        public boolean apply(String s) {
            return !(ignore_words.contains(s) || ALL_DECIMAL.matcher(s).matches());
        }
    };

    public TermExtract(TermDBProvider provider) {
        this.provider = provider;
        articulates = provider.getArticulateMap();
        combs = provider.getCombMap();
        documentFrequency = provider.getDocumentFrequencyMap();
    }
    public TermExtract() {}

    public TermExtract with_idf() {
        if (provider==null) throw new RuntimeException();
        with_idf = true;
        return this;
    }
    public TermExtract no_idf() {
        with_idf = false;
        return this;
    }
    public TermExtract no_lr() {
        lr_method=LR_METHOD.no_LR;
        return this;
    }
    public TermExtract lr_total() {
        lr_method=LR_METHOD.LR_TOTAL;
        return this;
    }
    public TermExtract lr_uniq() {
        lr_method=LR_METHOD.LR_UNIQ;
        return this;
    }
    public TermExtract lr_perplexity() {
        if (storage_mode) throw new RuntimeException();
        lr_method=LR_METHOD.PERPLEXITY;
        return this;
    }
    public TermExtract no_frq() {
        frq_method=FRQ_METHOD.no_FRQ;
        return this;
    }
    public TermExtract simple_frq() {
        frq_method=FRQ_METHOD.SIMPLE_FRQ;
        return this;
    }
    public TermExtract term_frq() {
        frq_method=FRQ_METHOD.TERM_FRQ;
        return this;
    }
    public TermExtract storage() {
        storage_mode=true;
        return this;
    }
    public TermExtract no_storage() {
        storage_mode=false;
        return this;
    }
    public TermExtract storage_df() {
        storage_df=true;
        return this;
    }
    public TermExtract no_storage_df() {
        storage_df=false;
        return this;
    }
    public TermExtract stat_mode() {
        stat_mode=true;
        return this;
    }
    public TermExtract no_stat() {
        stat_mode=false;
        return this;
    }

    public void setAverage_rate(double average_rate) { this.average_rate = average_rate; }
    public double getAverage_rate() { return average_rate; }

    public void study_term(String text) {
        String nfkc_text = Normalizer.normalize(text.toLowerCase(), Normalizer.Form.NFKC);
        List<Token> tokens = tokenizer.tokenize(nfkc_text);
        Map<String,Integer> noun_frq = get_noun_frq(tokens);
        if (storage_df) {
            storage_df_proc(noun_frq);
        }
        storage_stat_proc(noun_frq);
    }
    /**
     * #================================================================
     #
     # Calicurate LR of word. （専門用語の重要度の計算）
     # ------->> DB学習を分割
     * @param text 元文
     * @return キーワードリスト
     */
    public List<Term> calc_imp_word(String text) {
        String nfkc_text = Normalizer.normalize(text.toLowerCase(), Normalizer.Form.NFKC);
        List<Token> tokens = tokenizer.tokenize(nfkc_text);
        Map<String,Integer> noun_frq = get_noun_frq(tokens);
        // LR以外の重要度計算
        if (lr_method ==LR_METHOD.no_LR) {
            // 頻度を使っての重要度計算（学習機能を自動的にOFFにする)
            if (frq_method ==FRQ_METHOD.SIMPLE_FRQ)
                return calc_imp_by_HASH_Freq(noun_frq);
                // TFを使っての重要度計算（学習機能を自動的にOFFにする)
            else if (frq_method ==FRQ_METHOD.TERM_FRQ)
                return modify_noun_list_i( calc_imp_by_HASH_TF(noun_frq));
            else
                throw new RuntimeException("invalid param pattern frq_method and lr_method");
        }
        // パープレキシティを使ってのLR重要度計算（学習機能を自動的にOFFにする)
        else if (lr_method ==LR_METHOD.PERPLEXITY)
            return calc_imp_by_HASH_PP(noun_frq);
        // 学習機能（連接統計DB）を使ってのLR重要度計算
        else if (stat_mode)
            return calc_imp_by_DB(noun_frq);
        // 学習機能（連接統計DB）を使わないLR重要度計算
        else
            return calc_imp_by_HASH(noun_frq);

    }
    /**
     * #================================================================
     #
     # Calicurate LR of word. （専門用語の重要度の計算）
     #
     * @param text 元文
     * @return キーワードリスト
     */
    public List<Term> get_imp_word(String text) {
        String nfkc_text = Normalizer.normalize(text.toLowerCase(), Normalizer.Form.NFKC);
        List<Token> tokens = tokenizer.tokenize(nfkc_text);
        Map<String,Integer> noun_frq = get_noun_frq(tokens);
        if (storage_mode || storage_df) {
            // TODO:ロック
            if (storage_df) {
                storage_df_proc(noun_frq);
                // TODO: メソッド分割
            }
            if (storage_mode==true && lr_method !=LR_METHOD.no_LR) {
                storage_stat_proc(noun_frq);
            }
            if (storage_df)
                return null;
        }
        // LR以外の重要度計算
        if (lr_method ==LR_METHOD.no_LR) {
            // 頻度を使っての重要度計算（学習機能を自動的にOFFにする)
            if (frq_method ==FRQ_METHOD.SIMPLE_FRQ)
                return calc_imp_by_HASH_Freq(noun_frq);
            // TFを使っての重要度計算（学習機能を自動的にOFFにする)
            else if (frq_method ==FRQ_METHOD.TERM_FRQ)
                return modify_noun_list_i( calc_imp_by_HASH_TF(noun_frq));
            else
                throw new RuntimeException("invalid param pattern frq_method and lr_method");
        }
        // パープレキシティを使ってのLR重要度計算（学習機能を自動的にOFFにする)
        else if (lr_method ==LR_METHOD.PERPLEXITY)
            return calc_imp_by_HASH_PP(noun_frq);

        // 学習機能（連接統計DB）を使ってのLR重要度計算
        else if (stat_mode)
           return calc_imp_by_DB(noun_frq);
        // 学習機能（連接統計DB）を使わないLR重要度計算
        else
            return calc_imp_by_HASH(noun_frq);

    }

    /**
     * #=================================================================
     #
     # Calicurate importance of word by temporary HASH.
     # And return sorted list by importance.
     # （文中の語のみから重要度を計算し、重要度でソートした専門用語リスト
     #   を返す）
     * @param noun_frq 形態素解析結果Map
     * @return 処理結果
     */
    private List<Term> calc_imp_by_HASH(Map<String,Integer> noun_frq) {

        // 専門用語ごとにループ
        Set<String> n_cont_keys = Maps.filterKeys(noun_frq, NOUN_FILTER).keySet();
        Splitter sp = Splitter.on(SPLIT_SPACE);
        Joiner normal = Joiner.on(" ");
        // 計数ワーク
        Set<String> comb = Sets.newHashSet();
        Map<String,Integer> stat_pre = Maps.newHashMap();
        Map<String,Integer> stat_post = Maps.newHashMap();
        for (String cmp_noun : n_cont_keys ) {
            // メソッド IgnoreWords で指定した語と数値を無視する
            Deque<String> nouns = Lists.newLinkedList( Iterables.filter(sp.split(cmp_noun),IGNORE_FILTER));

            // 複合語の場合、連接語の情報をハッシュに入れる
            while (nouns.size() > 1) {
                String noun_pre = nouns.pop();
                String noun_post = nouns.peek();
                String comb_key = normal.join(noun_pre,noun_post);
                boolean first = comb.contains(comb_key);
                comb.add(comb_key);
                int imp = noun_frq.get(cmp_noun);
                int imp_stat;
                // 連接語の”延べ数”をとる場合
                if (lr_method==LR_METHOD.LR_TOTAL) {
                    imp_stat = stat_pre.get(noun_pre);
                    stat_pre.put(noun_pre,imp+imp_stat);
                    imp_stat = stat_post.get(noun_post);
                    stat_post.put(noun_post,imp+imp_stat);
                }
                // 連接語の異なり数をとる場合
                else if (lr_method==LR_METHOD.LR_UNIQ && first) {
                    imp_stat = stat_pre.get(noun_pre);
                    stat_pre.put(noun_pre,++imp_stat);
                    imp_stat = stat_post.get(noun_post);
                    stat_post.put(noun_post,++imp_stat);
                }
            }
        }
        Map<String, Integer> n_cont ;
        if (frq_method ==FRQ_METHOD.no_FRQ || frq_method ==FRQ_METHOD.SIMPLE_FRQ)
            n_cont = Maps.newHashMap(Maps.filterKeys(noun_frq, NOUN_FILTER));
        else
            n_cont = calc_imp_by_HASH_TF(noun_frq);
        // 専門用語ごとにループ
        Map<String,Double> n_imps = Maps.newHashMap();
        for (Map.Entry<String,Integer> entry : n_cont.entrySet()) {
            double imp = 1;
            int count = 0;
            String cmp_noun = entry.getKey();
            int frq = entry.getValue();
            // メソッド IgnoreWords で指定した語と数値を無視する
            Deque<String> nouns = Lists.newLinkedList( Iterables.filter(sp.split(cmp_noun),IGNORE_FILTER));
            for (String noun : nouns) {
                int pre = stat_pre.get(noun);
                int post = stat_post.get(noun);
                imp *= (pre+1) * (post+1);
                count++;
            }
            if (count==0) count=1;
            if (frq_method!=FRQ_METHOD.no_FRQ) {
                imp = Math.pow(imp,(1 /(2 * average_rate * count)));
                imp *= frq;
            }
            else {
                imp = Math.pow(imp,(1 /(2 * average_rate * count)));
            }
            n_imps.put(cmp_noun,imp);
        }
        return modify_noun_list(n_imps);

    }

    /**
     * #================================================================
     #
     # Calicurate importance of word by DB. （連接語統計DBから重要度を計算）
     # And return sorted list by importance.
     #
     * @param noun_frq 形態素解析結果Map
     * @return 処理結果
     */
    private List<Term> calc_imp_by_DB(Map<String,Integer> noun_frq) {
        if (average_rate==0)
            throw new RuntimeException("average_rate is invalid value \n");

        Map<String, Integer> n_cont ;
        if (frq_method ==FRQ_METHOD.no_FRQ || frq_method ==FRQ_METHOD.SIMPLE_FRQ)
            n_cont = Maps.newHashMap(Maps.filterKeys(noun_frq, NOUN_FILTER));
        else
            n_cont = calc_imp_by_HASH_TF(noun_frq);

        // 専門用語ごとにループ
        Map<String,Double> n_imps = Maps.newHashMap();
        Splitter sp = Splitter.on(SPLIT_SPACE);
        for (Map.Entry<String,Integer> entry : n_cont.entrySet()) {
            double imp = 1;
            int count = 0;
            String cmp_noun = entry.getKey();
            int frq = entry.getValue();
            // メソッド IgnoreWords で指定した語と数値を無視する
            Deque<String> nouns = Lists.newLinkedList( Iterables.filter(sp.split(cmp_noun),IGNORE_FILTER));
            for (String noun : nouns) {
                Articulate articulate;
                if ((articulate = articulates.get(noun))==null)
                    articulate = new Articulate();
                if (lr_method ==LR_METHOD.LR_TOTAL)
                    imp *= (articulate.total_pre+1)*(articulate.total_post+1);
                else if (lr_method ==LR_METHOD.LR_UNIQ)
                    imp *= (articulate.uniq_pre+1) * (articulate.uniq_post+1);
                count++;
            }
            if (count==0) count=1;
            if (frq_method!=FRQ_METHOD.no_FRQ) {
                imp = Math.pow(imp,(1 /(2 * average_rate * count)));
                imp *= frq;
            }
            else {
                imp = Math.pow(imp,(1 /(2 * average_rate * count)));
            }
            n_imps.put(cmp_noun,imp);
        }
        return modify_noun_list(n_imps);
    }

    /**
     * #=================================================================
     #
     # Calicurate importance of word by temporary HASH.(Perplexity)
     # And return sorted list by importance.
     # （文中の語のみから重要度を計算し、パープレキシティによる重要度で
     #  ソートした専門用語リストを返す）
     #
     * @param noun_frq 形態素解析結果Map
     * @return 処理結果
     */
    private List<Term> calc_imp_by_HASH_PP(Map<String, Integer> noun_frq) {
        Map<String, Integer> n_cont ;
        if (frq_method ==FRQ_METHOD.no_FRQ || frq_method ==FRQ_METHOD.SIMPLE_FRQ)
            n_cont = Maps.newHashMap(Maps.filterKeys(noun_frq, NOUN_FILTER));
        else
            n_cont = calc_imp_by_HASH_TF(noun_frq);

        // 専門用語ごとにループ
        Set<String> n_cont_keys = n_cont.keySet();
        Splitter sp = Splitter.on(SPLIT_SPACE);
        Map<String,Double> stat_pp = Maps.newHashMap();
        // 計数ワーク
        Map<String,Integer> stat_pre = Maps.newHashMap();
        Map<String,Integer> stat_post = Maps.newHashMap();
        Table<String,String,Integer> pre = HashBasedTable.create();
        Table<String,String,Integer> post = HashBasedTable.create();
        for (String cmp_noun : n_cont_keys ) {
            // メソッド IgnoreWords で指定した語と数値を無視する
            Deque<String> nouns = Lists.newLinkedList( Iterables.filter(sp.split(cmp_noun),IGNORE_FILTER));

            // 複合語の場合、連接語の情報をハッシュに入れる
            while (nouns.size() > 1) {
                String noun_pre = nouns.pop();
                String noun_post = nouns.peek();
                int imp = n_cont.get(cmp_noun);
                int imp_stat;
                imp_stat = stat_pre.get(noun_pre);
                stat_pre.put(noun_pre,imp+imp_stat);
                imp_stat = stat_post.get(noun_post);
                stat_post.put(noun_post,imp+imp_stat);
                imp_stat = pre.get(noun_post,noun_pre);
                pre.put(noun_post,noun_pre,++imp_stat);
                imp_stat = post.get(noun_pre,noun_post);
                pre.put(noun_pre,noun_post,++imp_stat);

                // 全ての単名詞について処理
                Set<String> stat_keys = Sets.union(stat_pre.keySet(),stat_post.keySet());
                for (String noun1 : stat_keys) {
                    double h = 0;
                    double work ;
                    // 単名詞のエントロピーを求める（後に連接するケース）
                    if (stat_pre.containsKey(noun1)) {
                        int imp1 = stat_pre.get(noun1);
                        for (Integer imp2 : post.row(noun1).values()) {
                            work = 1.0d * imp2 / (imp1 + 1);
                            h -= work * Math.log(work);
                        }
                    }
                    // 単名詞のエントロピーを求める（前に連接するケース）
                    if (stat_post.containsKey(noun1)) {
                        int imp1 = stat_pre.get(noun1);
                        for (Integer imp2 : pre.row(noun1).values()) {
                            work = 1.0d * imp2 / (imp1 + 1);
                            h -= work * Math.log(work);
                        }
                    }
                    stat_pp.put(noun1,h);
                }


            }
        }
        // 専門用語ごとにループ
        Map<String,Double> n_imps = Maps.newHashMap();
        for (String cmp_noun : n_cont_keys ) {
            double imp = 0;
            int count = 0;
            // メソッド IgnoreWords で指定した語と数値を無視する
            Deque<String> nouns = Lists.newLinkedList( Iterables.filter(sp.split(cmp_noun),IGNORE_FILTER));
            for (String noun : nouns) {
                if (stat_pp.containsKey(noun))
                    imp += stat_pp.get(noun);
                count++;
            }
            if (count==0) count=1;
            imp = imp / (2 * average_rate * count);
            if (frq_method !=FRQ_METHOD.no_FRQ)
                imp += Math.log(n_cont.get(cmp_noun)+1);
            imp = imp / Math.log(2);
            n_imps.put(cmp_noun,imp);
        }
        return modify_noun_list(n_imps);
    }

    static final Pattern PAT_ALPHA_ONLY = Pattern.compile("^\\p{L}+$");
    static final Pattern SPLIT_SPACE = Pattern.compile("\\s+");
    /**
     * #=================================================================
     #
     # Calicurate importance of word by temporary HASH on Term Frequency
     # And return sorted list by importance.
     # （TFを重要度とし、重要度でソートした専門用語リストを返す）
     #
     * @param noun_frq 形態素解析結果Map
     * @return 処理結果
     */
    private Map<String,Integer> calc_imp_by_HASH_TF(Map<String,Integer> noun_frq) {
        Map<String,Integer> nouns = Maps.filterKeys(noun_frq, NOUN_FILTER);
        Map<String,Integer> n_imps = Maps.newHashMap();
        // # TF重要度計算用の作業用配列
        ListMultimap<Integer,String> tf_data = MultimapBuilder.treeKeys().arrayListValues().build();
        Splitter sp = Splitter.on(SPLIT_SPACE);
        Joiner normal = Joiner.on(" ");
        for (String noun : nouns.keySet()) {
            Iterable<String> words = sp.split(noun);
            String noun_normal = normal.join(words);
            tf_data.put(Iterables.size(words),noun_normal);
            n_imps.put(noun_normal,nouns.get(noun));
        }
        SortedSet<Integer> tf_keys = (SortedSet<Integer>)tf_data.keySet();
        for (Integer wc : tf_keys) {
            List<String> nouns1 = tf_data.get(wc);
            for (String noun1 : nouns1 ) {

                for (Integer wc2 : tf_keys) {
                    if (wc2 <= wc)
                        continue;
                    List<String> nouns2 = tf_data.get(wc2);
                    for (String noun2 : nouns2 ) {
                        if (noun2.contains(noun1)) {
                            int imp = n_imps.get(noun1);
                            imp += n_imps.get(noun2);
                            n_imps.put(noun1,imp);
                        }
                    }
                }
            }
        }
        return n_imps;
    }

    /**
     * #=================================================================
     #
     # Calicurate importance of word by temporary HASH on  Frequency
     # And return sorted list by importance.
     # （文中の頻度を重要度とし、重要度でソートした専門用語リスト
     #   を返す）
     #
     * @param noun_frq 形態素解析結果Map
     * @return 処理結果
     */
    private List<Term> calc_imp_by_HASH_Freq(Map<String,Integer> noun_frq) {
        Map<String,Integer> nouns = Maps.filterKeys(noun_frq,NOUN_FILTER);
        return modify_noun_list_i(nouns);
    }

    /**
     * #=================================================================
     #
     #  Modfy extract word and inportance. [整数版]
     # （用語抽出結果の調整処理を行う）
     #
     * @param nouns 重要語抽出済みMap
     * @return 処理結果
     */
    private List<Term> modify_noun_list_i(Map<String,Integer> nouns) {
        Map<String,Double> n_imp;
        n_imp = Maps.transformValues(nouns,new Function<Integer, Double>() {
            @Override
            public Double apply(Integer integer) {
                return 1.0d * integer ;
            }
        });
        return modify_noun_list(n_imp);
    }
    /**
     * #=================================================================
     #
     #  Modfy extract word and inportance. [実数版]
     # （用語抽出結果の調整処理を行う）
     #
     * @param nouns 重要語抽出済みMap
     * @return 処理結果
     */
    private List<Term> modify_noun_list(Map<String,Double> nouns) {
        final int doc_count = documentFrequency.get(" ");
        Map<String,Double> n_imp;
        if (with_idf) {
            n_imp = Maps.transformEntries(nouns,new Maps.EntryTransformer<String, Double, Double>() {
                @Override
                public Double transformEntry(String s, Double imp) {
                    int  df = documentFrequency.get(s);
                    double idf = 1.0d * doc_count / df;
                    return (( Math.log(idf) / Math.log((double)2) ) + 1d ) * imp;
                }
            });
        }
        else {
            n_imp = nouns;
        }
        Ordering<Term> order = Ordering.natural().onResultOf(new Function<Term, Comparable>() {
            @Override
            public Comparable apply(Term term) {
                return term.score;
            }
        }).reverse();
        Iterable<Term> term_list = Iterables.transform(n_imp.entrySet(),new Function<Map.Entry<String, Double>, Term>() {
            @Override
            public Term apply(Map.Entry<String, Double> entry) {
                Term term = new Term();
                term.noun = entry.getKey();
                term.score = entry.getValue();
                return term;
            }
        });
        if (agglutinative_lang) {
            term_list = Iterables.transform(term_list,new Function<Term, Term>() {
                @Override
                public Term apply(Term term) {
                    term.noun = modify_agglutinative_lang(term.noun);
                    return term;
                }
            });
        }
        return order.sortedCopy(term_list);
    }


    /**
     * #================================================================
     #
     # Modify extract word list to readable
     # （日本語などの膠着言語[単語区切りなし]の複合語を、表示用に加工）
     #
     * @param noun 分かち書きされた重要語
     * @return 膠着された重要語
     */
    private String modify_agglutinative_lang(String noun) {
        StringBuilder sb = new StringBuilder();
        boolean eng;
        boolean eng_pre=false;
        // # 前後ともLatinなら半角空白空け
        // # 上記以外なら区切りなしで連結
        for (String s : Splitter.on(SPLIT_SPACE).split(noun)) {
            eng = PAT_ALPHA_ONLY.matcher(s).matches();
            if (eng && eng_pre)
                sb.append(" ");
            sb.append(s);
            eng_pre=eng;
        }
        return sb.toString();
    }

    /**
     * # ========================================================================
     # storage_df -- storage compound noun to Data Base File
     # (DF [Document Frequency]DBの情報を蓄積）
     #
     # usage: $self->storage_df(;
     #
     # ========================================================================
     */
    private void storage_df_proc(Map<String, Integer> noun_frq) {
        for (String key : noun_frq.keySet()) {
            if (!Strings.isNullOrEmpty(key) && key.length() < MAX_CMP_SIZE) {
                int df = documentFrequency.get(key);
                documentFrequency.put(key,++df);
            }
        }
        int df = documentFrequency.get(" ");
        documentFrequency.put(" ",++df);
    }

    /**
     * # ========================================================================
     # storage_stat -- storage compound noun to Data Base File
     # （連接統計DB[２種]に連接情報を蓄積）
     #
     * @param noun_frq 形態素解析結果Map
     */
    private void storage_stat_proc(Map<String, Integer> noun_frq) {

        Splitter sp = Splitter.on(SPLIT_SPACE);
        Joiner normal = Joiner.on(" ");
        for (Map.Entry<String, Integer> nounEntry : noun_frq.entrySet()) {
            String cmp_noun = nounEntry.getKey();
            int frq = nounEntry.getValue();
            if (!Strings.isNullOrEmpty(cmp_noun) && cmp_noun.length() < MAX_CMP_SIZE ) {
                Deque<String> nouns = Lists.newLinkedList( Iterables.filter(sp.split(cmp_noun),IGNORE_FILTER));
                while (nouns.size() > 1) {
                    String noun = nouns.pop();
                    String noun_post = nouns.peek();
                    String comb_key = normal.join(noun,noun_post);
                    boolean first = !combs.containsKey(comb_key);
                    /**
                     * 単名詞ごとの連接統計情報[Pre(N), Post(N)]を累積
                     */
                    Articulate articulate;
                    // post word (後ろにとりうる単名詞）
                    if ((articulate = articulates.get(noun))==null)
                        articulate = new Articulate();
                    if (first)
                        articulate.uniq_post += 1;

                    articulate.total_post += frq;
                    articulate.noun =  noun;
                    articulates.put(noun,articulate);
                    // pre word　（前にとりうる単名詞）
                    if ((articulate = articulates.get(noun_post))==null)
                        articulate = new Articulate();
                    if (first)
                        articulate.uniq_post += 1;

                    articulate.total_post += frq;
                    articulate.noun =  noun;
                    articulates.put(noun,articulate);
                    // 連接語とその頻度情報を累積
                    Integer comb_frq = 0 ;
                    if (combs.containsKey(comb_key))
                        comb_frq = combs.get(comb_key);
                    comb_frq += frq;
                    combs.put(comb_key,comb_frq);
                }
            }
        }
    }

    static final Pattern PAT_PUNCT = Pattern.compile("^[\\p{Ps}\\p{Pe}|\"';,]");
    static final Pattern PAT_ALPHA_PFX = Pattern.compile("^[\\p{L}]");
    static final Pattern PAT_ALPHA_SFX = Pattern.compile("[\\p{L}]$");
    static final Pattern PAT_ONLY_PUNCT = Pattern.compile("^[\\s\\p{Ps}\\p{Pe}\\p{Po}$]");
    static final Set<String> SURPLUS_SFX = Sets.newHashSet( "など", "ら", "上", "内", "型", "間", "中","毎","等");

    /**
     # get_noun_frq -- Get noun frequency.
     #                 The values of the hash are frequency of the noun.
     # （専門用語とその頻度を得るサブルーチン）
     * @param tokens Kuromojiの処理結果
     * @return 形態素解析結果Map
     */
    private Map<String,Integer> get_noun_frq(List<Token> tokens) {
        agglutinative_lang = true;
        Map<String,Integer> noun_frq = Maps.newHashMap();
        Deque<String> unknown = Lists.newLinkedList();
        Deque<String> terms = Lists.newLinkedList();
        boolean must = false;
        for (Token token :tokens) {
            String noun = token.getSurfaceForm();
            String[] features = token.getAllFeaturesArray();
            String part_of_speach = features[0];
            String cl_1 = features[1];
            String cl_2 = features[2];
            if (part_of_speach.equals("m語") && PAT_PUNCT.matcher(noun).matches()==false) {
                if (unknown.isEmpty()) {
                    unknown.add(noun);
                    continue;
                }
                else {
                    if (PAT_ALPHA_SFX.matcher( unknown.getLast() ).matches() &&
                            PAT_ALPHA_PFX.matcher(noun).matches()) {
                        unknown.add(noun);
                        continue;
                    }
                }
            }
            while (unknown.size()>0) {
                if (PAT_ONLY_PUNCT.matcher(unknown.getLast()).matches())
                    unknown.removeLast();
                else
                    break;
            }
            if (unknown.size()>0) {
                terms.add(Joiner.on("").join(unknown));
                unknown.clear();
            }
            if ((part_of_speach.equals("名詞") && (
                    cl_1.equals("一般") || cl_1.equals("サ変接続") ||
                    (cl_1.equals("接尾") && cl_2.equals("一般")) ||
                    (cl_1.equals("接尾") && cl_2.equals("サ変接続")) ||
                    cl_1.equals("固有名詞"))) ||
                (part_of_speach.equals("記号") && cl_1.equals("アルファベット")) ||
                (part_of_speach.equals("m語") && PAT_ONLY_PUNCT.matcher(noun).matches()==false)
                    ) {
                terms.add(noun);
                must=false;
                continue;
            }
            else if((part_of_speach.equals("名詞") && cl_1.equals("形容動詞語幹")) ||
                    (part_of_speach.equals("名詞") && cl_1.equals("ナイ形容詞語幹")))
            {
                terms.add(noun);
                must = true;
                continue;
            }
            else if(part_of_speach.equals("名詞") && cl_1.equals("接尾") && cl_2.equals("形容動詞語幹")){
                terms.add(noun);
                must = true;
                continue;
            }
            else if(part_of_speach.equals("動詞")){
                terms.clear();
            }
            else if (must == false){
                if (terms.size()>0) {
                    String first = terms.getFirst();
                    if (first.equals("本"))
                        terms.removeFirst();
                }
                if (terms.size()>0) {
                    String last = terms.getLast();
                    if (SURPLUS_SFX.contains( last) || last.matches("^\\s+$")) {
                        terms.removeLast();
                    }
                }
                if (terms.size()>0) {
                    String term = Joiner.on(" ").join(terms);
                    int frq = noun_frq.get(term) ;
                    noun_frq.put(term,++frq);
                    terms.clear();
                }
            }
            if (must==true)
                terms.clear();

            must = false;
        }
        return noun_frq;

    }
}