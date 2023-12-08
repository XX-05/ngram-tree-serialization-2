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

        assertEquals(5, encoded.length);
        assertEquals(4, encoded[0]);
        assertEquals('w', encoded[1]);
        assertEquals('o', encoded[2]);
        assertEquals('r', encoded[3]);
        assertEquals('d', encoded[4]);
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

        assertEquals('r', encoded[0]);
        assertEquals('o', encoded[1]);
        assertEquals('o', encoded[2]);
        assertEquals('t', encoded[3]);
        assertEquals(128 | 1, encoded[4] & 0xff);
        assertEquals(2, encoded[5]);
    }

    @Test
    public void testEncodeNodeWordBankReference_with_SmallAddress() {
        NGramTreeNode node = new NGramTreeNode("root");
        node.addWord("branch1");
        node.addWord("branch2");

        byte[] encoded8 = NTSFile.encodeNodeWordBankReference(8, node);

        assertEquals(192 | 1, encoded8[0] & 0xff);  // word bank reference indicator
        assertEquals(8, encoded8[1]);  // index in word bank
        assertEquals(128 | 1, encoded8[2] & 0xff);  // end word data indicator
        assertEquals(2, encoded8[3]);  // number of branches
    }

    @Test
    public void testEncodeNodeWordBankReference_with_BigAddress() {
        NGramTreeNode node = new NGramTreeNode("root");
        node.addWord("branch1");
        node.addWord("branch2");

        byte[] encoded8 = NTSFile.encodeNodeWordBankReference(13000, node);

        assertEquals(192 | 2, encoded8[0] & 0xff);  // word bank reference indicator
        assertEquals(50, encoded8[1]);  // index in word bank (13000 in big-endian)
        assertEquals((byte) 200, encoded8[2]);  // big end of 13000
        assertEquals(128 | 1, encoded8[3] & 0xff);  // end word data indicator
        assertEquals(2, encoded8[4]);  // number of branches
    }

    @Test
    public void testEncodeNodeWordBankReference_with_ZeroAddress() {
        NGramTreeNode node = new NGramTreeNode("root");
        node.addWord("branch1");
        node.addWord("branch2");

        byte[] encoded8 = NTSFile.encodeNodeWordBankReference(0, node);

        assertEquals(encoded8[0] & 0xff, 192);  // word bank reference indicator
        assertEquals(encoded8[1] & 0xff, (128 | 1));  // end word data indicator
        assertEquals(encoded8[2], 2);  // number of branches
    }

    @Test
    public void testCreateWordBankAddressMap() {
        List<String> wordBank = NTSFile.compileWordBank(baseTree);
        HashMap<String, Integer> addressMap = NTSFile.createWordBankAddressMap(wordBank);
        for (Map.Entry<String, Integer> e : addressMap.entrySet()) {
            int index = e.getValue();
            String word = e.getKey();

            assertEquals(word, wordBank.get(index));
        }
    }

    @AfterAll
    public static void writeSerializedTree() throws IOException {
        NTSFile.serializeBinary(baseTree, new FileOutputStream("wordbank_serial.nts"));
        System.out.println("Wrote 'wordbank_serial.nts'");
    }
}
