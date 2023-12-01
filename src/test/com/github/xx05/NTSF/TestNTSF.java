package com.github.xx05.NTSF;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class TestNTSF {
    public static void testEncodeWordForWordBank() {
        byte[] encoded = NTSFile.encodeWordForWordBank("word");
        assert encoded.length == 5;
        assert encoded[0] == 4;
        assert encoded[1] == 'w';
        assert encoded[2] == 'o';
        assert encoded[3] == 'r';
        assert encoded[4] == 'd';
    }

    public static void testWriteWordBank(NGramTreeNode rootNode) throws IOException {
        ByteArrayOutputStream fw = new ByteArrayOutputStream();
        List<String> wordBank = NTSFile.compileWordBank(rootNode);
        NTSFile.writeWordBank(rootNode, fw);
        InputStream fr = new ByteArrayInputStream(fw.toByteArray());
        List<String> reconstructedWordBank = new ArrayList<>();

        int wordLength;
        while ((wordLength = fr.read()) != -1) {
            StringBuilder wordBuffer = new StringBuilder();
            for (int i = 0; i < wordLength; i ++) {
                wordBuffer.append((char) fr.read());
            }
            reconstructedWordBank.add(wordBuffer.toString());
        }

        assert wordBank.equals(reconstructedWordBank);

        try (FileOutputStream fileOutputStream = new FileOutputStream("wordBank.nts")) {
            fileOutputStream.write(fw.toByteArray());
        }
    }

    public static void testEncodeNodeStandard() {
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

    public static void testEncodeNodeWordBankReference_with_SmallAddress() {
        NGramTreeNode node = new NGramTreeNode("root");
        node.addWord("branch1");
        node.addWord("branch2");

        byte[] encoded8 = NTSFile.encodeNodeWordBankReference(8, node);

        assert (encoded8[0] & 0xff) == (192 | 1);  // word bank reference indicator
        assert encoded8[1] == 8;  // index in word bank
        assert (encoded8[2] & 0xff) == (128 | 1);  // end word data indicator
        assert encoded8[3] == 2; // number of branches
    }

    public static void testEncodeNodeWordBankReference_with_BigAddress() {
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

    public static void main(String[] args) throws IOException, MalformedSerialBinaryException {
        testEncodeWordForWordBank();
        testEncodeNodeStandard();
        testEncodeNodeWordBankReference_with_SmallAddress();
        testEncodeNodeWordBankReference_with_BigAddress();

        NGramTreeNode baseTree = NGramTreeNodeFileHandler.deserializeBinary(new FileInputStream("base_tree.ntsf"));
        System.out.println(Arrays.toString(baseTree.predictNextWord("hi my name is".split(" "))));
        testWriteWordBank(baseTree);
    }
}
