package com.github.nisin.termextract;

import com.github.nisin.termextract.model.Comb;
import com.github.nisin.termextract.model.Df;
import com.github.nisin.termextract.model.Stat;
import com.github.nisin.termextract.model.Term;
import com.google.common.base.Charsets;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import junit.framework.TestCase;

import java.io.*;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 *
 * Created by Shoichi on 2014/02/19.
 */
public class TermExtractTest extends TestCase {
    public void testModify_agglutinative_lang() throws Exception {
        FileTermDB termDB = new FileTermDB();
        TermExtract termExtract = new TermExtract(termDB);
        Method m = TermExtract.class.getDeclaredMethod("modify_agglutinative_lang",String.class);
        m.setAccessible(true);
        String noun;
        noun = (String) m.invoke(termExtract, "不妊 治療");
        assertEquals(noun, "不妊治療");
        noun = (String) m.invoke(termExtract, "ネット 企業");
        assertEquals(noun, "ネット企業");
        noun = (String) m.invoke(termExtract, "Apple 党");
        assertEquals(noun, "Apple党");
        noun = (String) m.invoke(termExtract, "偽 Coin 被害");
        assertEquals(noun, "偽Coin被害");
        noun = (String) m.invoke(termExtract, "追求型 Test case 問題");
        assertEquals(noun, "追求型Test case問題");
    }

    public void testStudy_term() throws Exception {
        FileTermDB termDB = new FileTermDB();
        TermExtract termExtract = new TermExtract(termDB);
        termExtract.storage().storage_df().with_idf().lr_total().term_frq().stat_mode();

        termExtract.study_term("テスト悩み\n" +
                "テスト返答テスト返答テスト返答テスト返答\n" +
                "テスト返答テスト返答テスト返答テスト返答\n" +
                "テスト返答テスト返答テスト返答テスト返答");

        int comb_size = termDB.getCombMap().size();
        assertTrue(comb_size>0);
        int stat_size = termDB.getStatMap().size();
        assertTrue(stat_size>0);
        int df_size = termDB.getDocumentFrequencyMap().size();
        assertTrue(df_size>0);

        termExtract.study_term("肩こりの相談テストです。\n" +
                "回答てすてす！回答てすてす！回答てすてす！\n" +
                "\n" +
                "回答てすてす！回答てすてす！回答てすてす！回答てすてす！回答てすてす！回答てすてす！回答てすてす！回答てすてす！回答てすてす！回答てすてす！回答てすてす！回答てすてす！回答てすてす！回答てすてす！\n");
        assertTrue(comb_size<termDB.getCombMap().size());
        assertTrue(stat_size<termDB.getStatMap().size());
        assertTrue(df_size<termDB.getDocumentFrequencyMap().size());
    }

    public void testCalc_imp_word() throws Exception {
        FileTermDB termDB = new FileTermDB();
        TermExtract termExtract = new TermExtract(termDB);
        termExtract.storage().storage_df().with_idf().lr_total().term_frq().stat_mode();

        termExtract.study_term("テスト悩み\n" +
                "テスト返答テスト返答テスト返答テスト返答\n" +
                "テスト返答テスト返答テスト返答テスト返答\n" +
                "テスト返答テスト返答テスト返答テスト返答");
        termExtract.study_term("肩こりの相談テストです。\n" +
                "回答てすてす！回答てすてす！回答てすてす！\n" +
                "\n" +
                "回答てすてす！回答てすてす！回答てすてす！回答てすてす！回答てすてす！回答てすてす！回答てすてす！回答てすてす！回答てすてす！回答てすてす！回答てすてす！回答てすてす！回答てすてす！回答てすてす！\n");

        termExtract.calc_imp_word("テスト悩み\n" +
                "テスト返答テスト返答テスト返答テスト返答\n" +
                "テスト返答テスト返答テスト返答テスト返答\n" +
                "テスト返答テスト返答テスト返答テスト返答");

        termExtract.no_stat();
        termExtract.calc_imp_word("肩こりの相談テストです。\n" +
                "回答てすてす！回答てすてす！回答てすてす！\n" +
                "\n" +
                "回答てすてす！回答てすてす！回答てすてす！回答てすてす！回答てすてす！回答てすてす！回答てすてす！回答てすてす！回答てすてす！回答てすてす！回答てすてす！回答てすてす！回答てすてす！回答てすてす！\n");

    }
    public void testWikipedia() throws Exception {
        FileTermDB termDB = new FileTermDB();
        TermExtract termExtract = new TermExtract(termDB);
        termExtract.storage().storage_df().with_idf().lr_total().term_frq().stat_mode();
        if (false) {
            File work = File.createTempFile("wikipedia_vectors",".tsv");
            Writer sink = Files.newWriter(work,Charsets.UTF_8);
            List<String> lines = Resources.readLines(Resources.getResource("wikipedia.txt"), Charsets.UTF_8);
            for (String line : lines) {
                termExtract.study_term(line);
            }
            int i = 0;
            StringBuilder sb = new StringBuilder();
            for (String line : lines) {
                sb.setLength(0);
                sb.append(i++);
                for (Term term : termExtract.calc_imp_word(line)) {
                    sb.append("\t").append(term.noun);
                    sb.append("\t").append(term.score);
                }
                sink.write(sb.append("\n").toString());
            }
            System.out.println(work.getPath());
        }
    }


    /**
     * シリアライズでDBをファイルに保存する。
     *
     */
    private static class FileTermDB implements TermDBProvider,Serializable {
        private static final long serialVersionUID = 8454196482033077208L;
        private Map<String, Stat> statMap;
        private Map<String, Comb> combMap;
        private Map<String, Df> dfMap;

        static FileTermDB load(File file) throws IOException, ClassNotFoundException {
            return load(Files.asByteSource(file).openStream());
        }

        private static FileTermDB load(InputStream inputStream) throws IOException, ClassNotFoundException {
            ObjectInputStream ois = new ObjectInputStream(inputStream);
            FileTermDB fileTermDB = (FileTermDB) ois.readObject();
            return fileTermDB;
        }
        public void save(File file) throws IOException {
            save(Files.asByteSink(file).openStream());
        }

        private void save(OutputStream stream) throws IOException {
            ObjectOutputStream oos = new ObjectOutputStream(stream);
            oos.writeObject(this);
        }

        @Override
        public Map<String, Stat> getStatMap() {
            if (statMap==null)
                statMap=Maps.newHashMap();
            return statMap;
        }

        @Override
        public Map<String, Comb> getCombMap() {
            if (combMap==null)
                combMap=Maps.newHashMap();
            return combMap;
        }

        @Override
        public Map<String, Df> getDocumentFrequencyMap() {
            if (dfMap==null)
                dfMap=Maps.newHashMap();
            return dfMap;
        }
    }
}
