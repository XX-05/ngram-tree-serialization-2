package com.github.xx05.NTSF;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class NTSFile {

    static final int BANK_REF_INDICATOR_MASK = 192;
    static final int END_WORD_MASK = 128;

    /**
     * Returns the minimum number of bytes needed to
     * represent the given number.
     *
     * @param number An integer to measure in bytes
     * @return The number of bytes needed to store the given number
     */
    private static int computeByteWidth(int number) {
        return (int) Math.ceil(Math.log(number + 1) / Math.log(2) / 8.0);
    }

    /**
     * Returns a list of repeated node words in the given
     * NGram Tree branch with head rootNode sorted least
     * to greatest by number of occurrences in the tree.
     *
     * @param rootNode The root node of the tree to traverse
     * @return A sorted list of repeated words in the tree
     */
    static List<String> findRepeats(NGramTreeNode rootNode) {
        HashMap<String, Integer> counts = new HashMap<>();
        Stack<NGramTreeNode> stack = new Stack<>();

        stack.add(rootNode);

        while (!stack.isEmpty()) {
            NGramTreeNode node = stack.pop();
            String word = node.getWord();
            int newCount = 1;
            if (counts.containsKey(word)) {
                newCount =  counts.get(word) + 1;
            }
            counts.put(word, newCount);
            stack.addAll(List.of(node.getChildren()));
        }

        counts.entrySet().removeIf(entry -> entry.getValue() == 1);
        List<String> keysList = new ArrayList<>(counts.keySet());
        keysList.sort(Comparator.comparing(String::length));

        return keysList;
    }

    /**
     * Takes a list of repeated words from a NGramTree and removes entries
     * that would not see a storage size reduction by inclusion in a word bank.
     * I.e. the number of bytes needed to store the word bank reference
     * indicator plus the byte length of the word bank address is greater
     * than the length of the word. Words that are too long (>256 length) are
     * also removed.
     *
     * @param repeatedWords A list of repeated words from a NGramTree to filter.
     */
    static void filterRepeats(List<String> repeatedWords) {
        // TODO: Ensure that max array reference (array length) can be stored
        //  in 64 bytes (its enormous you're chill)
        for (int i = 0; i < repeatedWords.size(); i ++) {
            String word = repeatedWords.get(i);
            if (computeByteWidth(i) + 2 >= word.length() || word.length() > 256) {
                repeatedWords.remove(i);
                i --;
            }
        }
    }

    /**
     * Compiles a word bank from the repeated node words in the given NGram Tree branch.
     * The word bank is sorted from least to greatest by the number of occurrences in the tree.
     * Entries that would not see a storage size reduction by inclusion in the word bank
     * are filtered out based on the criteria that the number of bytes needed to store
     * the word bank reference indicator plus the byte length of the word bank address
     * must be greater than the length of the word.
     *
     * @param rootNode The root node of the NGram Tree branch to traverse.
     * @return A sorted and filtered list of repeated words forming the compiled word bank.
     */
    static List<String> compileWordBank(NGramTreeNode rootNode) {
        List<String> repeats = findRepeats(rootNode);
        filterRepeats(repeats);
        return repeats;
    }

    /**
     * Creates a map between words in the word bank and their indexes
     * for fast lookup during encoding.
     *
     * @param wordBank The word bank to map
     * @return A mapping of word bank entries to their indexes
     */
    static HashMap<String, Integer> createWordBankAddressMap(List<String> wordBank) {
        HashMap<String, Integer> addressMap = new HashMap<>();
        for (int i = 0; i < wordBank.size(); i ++) {
            addressMap.put(wordBank.get(i), i);
        }
        return addressMap;
    }

    /**
     * Encodes a word as bytes for the word bank
     * in the binary tree serialization.
     *
     * @param word The word to encode.
     * @return The encoded word block.
     */
    static byte[] encodeWordForWordBank(String word) {
        int length = word.length();
        byte[] encoded = new byte[length + 1];

        encoded[0] = (byte) length;
        System.arraycopy(word.getBytes(StandardCharsets.US_ASCII), 0, encoded, 1, length);

        return encoded;
    }


    /**
     * Writes the compiled word bank for the given NGram Tree branch to an OutputStream and returns
     * the word bank. Each word is encoded as bytes in a block with the format | word_length || word_data |.
     * The word bank terminates with the 0 byte which indicates that tree data will follow.
     *
     * @param rootNode The root node of the NGram Tree branch.
     * @param outputStream The OutputStream to write the word bank to.
     * @throws IOException If an I/O error occurs during the writing process.
     */
    static List<String> writeWordBank(NGramTreeNode rootNode, OutputStream outputStream) throws IOException {
        List<String> wordBank = compileWordBank(rootNode);

        for (String word : wordBank) {
            outputStream.write(encodeWordForWordBank(word));
        }

        outputStream.write(0);

        return wordBank;
    }

    /**
     * Encodes a node in the standard format for binary tree serialization.
     *
     * @param node The NGramTreeNode to encode.
     * @return The encoded node.
     * @throws TreeSerializationException If there is an issue during tree serialization.
     */
    static byte[] encodeNodeStandard(NGramTreeNode node) throws TreeSerializationException {
        int nChildren = node.getBranchCount();
        int nChildrenByteWidth = computeByteWidth(nChildren);

        if (nChildrenByteWidth > 63) {
            throw new TreeSerializationException("The number of children of this node exceed 2**63 - 1.");
        }

        byte[] wordBytes = node.getWord().getBytes(StandardCharsets.US_ASCII);

        // the size of the encoded node is equal to the length of the word plus
        // the byte width of nChildren plus 1 for the END_WORD byte.
        byte[] encoded = new byte[wordBytes.length + nChildrenByteWidth + 1];
        // copy the node's word into the encoded array
        System.arraycopy(wordBytes, 0, encoded, 0, wordBytes.length);

        // END_WORD byte storing the number of bytes encoding N_CHILDREN like so
        // | 1 0 | | (6 byte N_CHILDREN_SIZE) |
        encoded[wordBytes.length] = (byte) (128 | nChildrenByteWidth);

        // copy nChildren bytes into tail-end of encoded array big-endian
        for (int i = 0; i < nChildrenByteWidth; i++) {
            encoded[encoded.length - i - 1] = (byte) (nChildren >> (i * 8));
        }

        return encoded;
    }

    /**
     * Encodes a node that has its word stored in the
     * word bank for binary tree serialization.
     *
     * @param wordBankAddress The address of the word in the word bank.
     * @param node The NGramTreeNode to encode.
     * @return The encoded node with a word bank reference.
     */
    static byte[] encodeNodeWordBankReference(int wordBankAddress, NGramTreeNode node) {
        int nChildren = node.getBranchCount();
        int nChildrenByteWidth = computeByteWidth(nChildren);
        int addressByteWidth = computeByteWidth(wordBankAddress);

        byte[] encoded = new byte[addressByteWidth + nChildrenByteWidth + 2];

        // BANK_REF byte indicating that this node's word is stored in the
        // word bank as well as storing the byte width of the word's index in the bank like so
        // | 1 1 | | (6 byte ADDRESS_SIZE) |
        encoded[0] = (byte) (BANK_REF_INDICATOR_MASK | addressByteWidth);

        // copy word bank address into encoded array big-endian
        for (int i = 0; i < addressByteWidth; i ++) {
            encoded[addressByteWidth - i] = (byte) (wordBankAddress >> (i * 8));
        }

        // END_WORD byte storing the number of bytes encoding N_CHILDREN like so
        // | 1 0 | | (6 byte N_CHILDREN_SIZE) |
        encoded[addressByteWidth + 1] = (byte) (END_WORD_MASK | nChildrenByteWidth);

        // copy nChildren bytes into tail-end of encoded array big-endian
        for (int i = 0; i < nChildrenByteWidth; i++) {
            encoded[encoded.length - i - 1] = (byte) (nChildren >> (i * 8));
        }

        return encoded;
    }

    /**
     * Writes the serialized binary form of the NGramTreeNode to an OutputStream.
     *
     * @param rootNode The root node of the NGram Tree to serialize.
     * @param outputStream The OutputStream to write the binary data to.
     * @throws IOException If an I/O error occurs during the writing process.
     * @throws TreeSerializationException If there is an issue during tree serialization.
     */
    public static void serializeBinary(NGramTreeNode rootNode, OutputStream outputStream) throws IOException, TreeSerializationException {
        List<String> wordBank = writeWordBank(rootNode, outputStream);
        HashMap<String, Integer> wordBankAddressMap = createWordBankAddressMap(wordBank);

        Stack<NGramTreeNode> stack = new Stack<>();
        stack.add(rootNode);

        while (!stack.isEmpty()) {
            NGramTreeNode node = stack.pop();

            if (wordBankAddressMap.containsKey(node.getWord())) {
                outputStream.write(encodeNodeWordBankReference(wordBankAddressMap.get(node.getWord()), node));
            } else {
                outputStream.write(encodeNodeStandard(node));
            }

            stack.addAll(List.of(node.getChildren()));
        }
    }

    /**
     * Parses the word bank from an InputStream and returns the reconstructed word bank.
     *
     * @param inputStream The InputStream containing the binary encoded word bank.
     * @return The reconstructed word bank.
     * @throws IOException If an I/O error occurs during the parsing process.
     */
    static List<String> parseWordBank(InputStream inputStream) throws IOException {
        List<String> reconstructedWordBank = new ArrayList<>();

        int wordLength;
        while ((wordLength = inputStream.read()) != 0) {
            StringBuilder wordBuffer = new StringBuilder();
            for (int i = 0; i < wordLength; i ++) {
                wordBuffer.append((char) inputStream.read());
            }
            reconstructedWordBank.add(wordBuffer.toString());
        }

        return reconstructedWordBank;
    }

    /**
     * The magic of the tree reconstruction algorithm:
     * deflateStack pops the previous node (parent) in the stack, adds the newNodeData to it,
     * reduces the parent's remaining children value by 1, then adds the parent and child data
     * back onto to the stack if their remaining children is greater than 0. After this,
     * the stack is 'deflated' and all nodes with 0 remaining children are removed from the top
     * of the stack until a node with > 0 remaining children is reached.
     *
     * @param stack The stack populated by previous deflateStack calls which stores the intermediate state
     *              of the node reconstruction.
     * @param newNodeData A Pair storing the new node to add and the number of children that must be attached
     *                    to it during reconstruction.
     */
    static void deflateStack(Stack<Pair<NGramTreeNode, Integer>> stack, Pair<NGramTreeNode, Integer> newNodeData) {
        Pair<NGramTreeNode, Integer> parentData = stack.get(stack.size() - 1);
        parentData.getFirst().addChild(newNodeData.getFirst());
        parentData.setSecond(parentData.getSecond() - 1);

        if (parentData.getSecond() == 0)
            // pop here instead of originally popping and reinserting to reduce unnecessary stack manipulation
            stack.pop();
        if (newNodeData.getSecond() > 0)
            stack.add(newNodeData);

        // ** deflate stack **
        for (int j = stack.size() - 1; j > 0; j--) {
            Pair<NGramTreeNode, Integer> n = stack.get(j);
            if (n.getSecond() > 0) {
                break;
            }
            stack.pop();
        }
    }

    /**
     * Converts an ArrayList of (character) bytes to a String.
     *
     * @param buff The ArrayList containing the byte data.
     * @return The String representation of the byte data.
     */
    static String parseBuffToString(ArrayList<Byte> buff) {
        StringBuilder word = new StringBuilder();
        for (Byte b : buff) {
            word.append((char) b.byteValue());
        }
        return word.toString();
    }

    /**
     * Parses the next byteWidth bytes from inputStream and returns the
     * big endian integer represented by them.
     *
     * @param inputStream The input stream to read from
     * @param byteWidth The number of bytes storing nChildren
     * @return The parsed nChildren value
     * @throws IOException when there is an issue reading from inputStream
     */
    static int parseBigEndianInteger(InputStream inputStream, int byteWidth) throws IOException {
        int nChildren = 0;
        for (int i = 0; i < byteWidth; i++) {
            nChildren = (nChildren & 0xFF) << 8 | (inputStream.read() & 0xFF);
        }
        return nChildren;
    }

    /**
     * Deserializes a binary encoded NGramTreeNode from an InputStream.
     *
     * @param inputStream The InputStream containing the binary encoded data.
     * @return The root NGramTreeNode reconstructed from the binary encoded data.
     * @throws IOException If an I/O error occurs while reading from the stream.
     * @throws MalformedSerialBinaryException If the binary data is malformed or cannot be parsed.
     */
    public static NGramTreeNode deserializeBinary(InputStream inputStream) throws IOException, MalformedSerialBinaryException {
        List<String> wordBank = parseWordBank(inputStream);

        NGramTreeNode rootNode = null;
        Stack<Pair<NGramTreeNode, Integer>> stack = new Stack<>();

        ArrayList<Byte> buff = new ArrayList<>();

        int currByte;
        while ((currByte = inputStream.read()) != -1) {
            if (currByte >= END_WORD_MASK) {
                // parses buff into word for the standard block case
                // if the current block is a backreference, word is set to an empty string
                String word = parseBuffToString(buff);

                if (currByte >= BANK_REF_INDICATOR_MASK) {
                    int address = parseBigEndianInteger(inputStream, currByte & 63);
                    word = wordBank.get(address);
                    currByte = inputStream.read(); // read byte storing nChildren byte length to match the standard case
                }

                int nChildren = parseBigEndianInteger(inputStream, currByte & 63);
                buff.clear();

                NGramTreeNode node = new NGramTreeNode(word);
                Pair<NGramTreeNode, Integer> newNodeData = new Pair<>(node, nChildren);

                if (rootNode == null) {
                    rootNode = node;
                    stack.add(newNodeData);
                    continue;
                }

                deflateStack(stack, newNodeData);  // tree reconstruction magic
            } else {
                buff.add((byte) currByte);
            }
        }

        if (rootNode == null) {
            throw new MalformedSerialBinaryException("Could not parse any nodes from the given data!");
        }

        return rootNode;
    }
}
