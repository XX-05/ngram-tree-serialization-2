package com.github.xx05.NTSF;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NTSFileTests {
    static NGramTreeNode baseTree;

    @BeforeAll
    static void initializeTree() throws IOException, MalformedSerialBinaryException {
        baseTree = NGramTreeNodeFileHandler.deserializeBinary(new FileInputStream("base_tree.ntsf"));
    }

    @Test
    public void testEncodeWordForWordBank() {
        byte[] encoded = NTSFile.encodeWordForWordBank("word");
        assert encoded.length == 5;
        assert encoded[0] == 4;
        assert encoded[1] == 'w';
        assert encoded[2] == 'o';
        assert encoded[3] == 'r';
        assert encoded[4] == 'd';
    }

    @Test
    public void testWriteWordBank() throws IOException {
        ByteArrayOutputStream fw = new ByteArrayOutputStream();
        List<String> wordBank = NTSFile.compileWordBank(baseTree);
        NTSFile.writeWordBank(baseTree, fw);

        byte[] wordBankBytes = fw.toByteArray();
        assertEquals(wordBankBytes[wordBankBytes.length - 1], 0);  // ensure word bank terminates with 0 byte

        InputStream fr = new ByteArrayInputStream(wordBankBytes);
        List<String> reconstructedWordBank = new ArrayList<>();

        int wordLength;
        while ((wordLength = fr.read()) != 0) {
            StringBuilder wordBuffer = new StringBuilder();
            for (int i = 0; i < wordLength; i ++) {
                wordBuffer.append((char) fr.read());
            }
            reconstructedWordBank.add(wordBuffer.toString());
        }

        assertEquals(wordBank, reconstructedWordBank);
    }

    @Test
    public void testEncodeNodeStandard() {
        NGramTreeNode node = new NGramTreeNode("root");
        node.addWord("branch1");
        node.addWord("branch2");

        byte[] encoded = NTSFile.encodeNodeStandard(node);
        assert encoded[0] == 'r';
        assert encoded[1] == 'o';
        assert encoded[2] == 'o';
        assert encoded[3] == 't';
        assert (encoded[4] & 0xff) == (128 | 1);
        assert encoded[5] == 2;
    }

    @Test
    public void testEncodeNodeWordBankReference_with_SmallAddress() {
        NGramTreeNode node = new NGramTreeNode("root");
        node.addWord("branch1");
        node.addWord("branch2");

        byte[] encoded8 = NTSFile.encodeNodeWordBankReference(8, node);

        assert (encoded8[0] & 0xff) == (192 | 1);  // word bank reference indicator
        assert encoded8[1] == 8;  // index in word bank
        assert (encoded8[2] & 0xff) == (128 | 1);  // end word data indicator
        assert encoded8[3] == 2;  // number of branches
    }

    @Test
    public void testEncodeNodeWordBankReference_with_BigAddress() {
        NGramTreeNode node = new NGramTreeNode("root");
        node.addWord("branch1");
        node.addWord("branch2");

        byte[] encoded8 = NTSFile.encodeNodeWordBankReference(13000, node);

        assert (encoded8[0] & 0xff) == (192 | 2);  // word bank reference indicator
        assert encoded8[1] == 50;  // index in word bank (13000 in big-endian)
        assert encoded8[2] == (byte) 200;  // big end of 13000
        assert (encoded8[3] & 0xff) == (128 | 1);  // end word data indicator
        assert encoded8[4] == 2;  // number of branches
    }

    @Test
    public void testEncodeNodeWordBankReference_with_ZeroAddress() {
        NGramTreeNode node = new NGramTreeNode("root");
        node.addWord("branch1");
        node.addWord("branch2");

        byte[] encoded8 = NTSFile.encodeNodeWordBankReference(0, node);

        assert (encoded8[0] & 0xff) == 192;  // word bank reference indicator
        assert (encoded8[1] & 0xff) == (128 | 1);  // end word data indicator
        assert encoded8[2] == 2;  // number of branches
    }

    @Test
    public void testCreateWordBankAddressMap() {
        List<String> wordBank = NTSFile.compileWordBank(baseTree);
        HashMap<String, Integer> addressMap = NTSFile.createWordBankAddressMap(wordBank);
        for (Map.Entry<String, Integer> e : addressMap.entrySet()) {
            int index = e.getValue();
            String word = e.getKey();

            assert wordBank.get(index).equals(word);
        }
    }

    @AfterAll
    public static void writeSerializedTree() throws IOException {
        NTSFile.serializeBinary(baseTree, new FileOutputStream("wordbank_serial.nts"));
        System.out.println("Wrote 'wordbank_serial.nts'");
    }
}
