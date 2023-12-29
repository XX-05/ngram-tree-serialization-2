package com.github.xx05.NTSF;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the NTSFile class, which handles encoding and decoding of N-Gram trees to and from binary files.
 */
class NTSFileTests {
    /**
     * The base N-Gram tree used for testing. Initialized once before running any test methods.
     */
    static NGramTreeNode baseTree;

    /**
     * Initializes the base N-Gram tree from a serialized binary file before running any test methods.
     *
     * @throws IOException                      If an I/O error occurs while reading the binary file.
     * @throws MalformedSerialBinaryException  If the binary file has a malformed serialization format.
     */
    @BeforeAll
    static void initializeTree() throws IOException, MalformedSerialBinaryException {
        baseTree = NGramTreeNodeFileHandler.deserializeBinary(new FileInputStream("base_tree.ntsf"));
    }

    /**
     * Tests the encoding of a word for the word bank and verifies the correctness of the encoded byte array.
     */
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

    /**
     * Tests the writing of the word bank to an output stream and verifies the correctness of the written data.
     *
     * @throws IOException If an I/O error occurs while writing or reading from the streams.
     */
    @Test
    public void testWriteWordBank_and_ParseWordBank() throws IOException {
        ByteArrayOutputStream fw = new ByteArrayOutputStream();
        List<String> wordBank = NTSFile.compileWordBank(baseTree);
        NTSFile.writeWordBank(baseTree, fw);

        byte[] wordBankBytes = fw.toByteArray();
        assertEquals(wordBankBytes[wordBankBytes.length - 1], 0);  // ensure word bank terminates with 0 byte

        InputStream fr = new ByteArrayInputStream(wordBankBytes);
        List<String> reconstructedWordBank = NTSFile.parseWordBank(fr);

        assertEquals(wordBank, reconstructedWordBank);
    }

    /**
     * Tests the encoding of an N-Gram tree node in standard format and verifies the correctness of the encoded byte array.
     */
    @Test
    public void testEncodeNodeStandard() throws TreeSerializationException {
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

    /**
     * Tests the encoding of an N-Gram tree node with a word bank reference and verifies the correctness of the encoded byte array
     * with a small address.
     */
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

    /**
     * Tests the encoding of an N-Gram tree node with a word bank reference and verifies the correctness of the encoded byte array
     * with a big address.
     */
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

    /**
     * Tests the encoding of an N-Gram tree node with a word bank reference and verifies the correctness of the encoded byte array
     * with a zero address.
     */
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

    /**
     * Tests the creation of a word bank address map and verifies the correctness of the generated map.
     */
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

    @Test
    public void testSerializeBinary_and_DeserializeBinary() throws TreeSerializationException, IOException, MalformedSerialBinaryException {
        ByteArrayOutputStream fw = new ByteArrayOutputStream();
        NTSFile.serializeBinary(baseTree, fw);
        ByteArrayInputStream fr = new ByteArrayInputStream(fw.toByteArray());
        NGramTreeNode deserializedTree = NTSFile.deserializeBinary(fr);
        assertTrue(baseTree.equals(deserializedTree));
    }

    /**
     * Writes the serialized base N-Gram tree to a binary file after running all test methods.
     *
     * @throws IOException If an I/O error occurs while writing to the output stream.
     */
    @AfterAll
    public static void writeSerializedTree() throws IOException, TreeSerializationException {
        NTSFile.serializeBinary(baseTree, new FileOutputStream("wordbank_serial.nts"));
        System.out.println("Wrote 'wordbank_serial.nts'");
    }
}
