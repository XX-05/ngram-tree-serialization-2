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

    public static void main(String[] args) throws IOException, MalformedSerialBinaryException {
        testEncodeWordForWordBank();

        NGramTreeNode baseTree = NGramTreeNodeFileHandler.deserializeBinary(new FileInputStream("base_tree.ntsf"));
        System.out.println(Arrays.toString(baseTree.predictNextWord("hi my name is".split(" "))));
        testWriteWordBank(baseTree);
    }
}
