package com.example.vrmmaven;

import java.util.ArrayList;
import java.util.List;

public class BST {
    // Node class representing each node in the BST
    static class Node {
        int value;
        Node left, right;

        Node(int value) {
            this.value = value;
            left = right = null;
        }
    }

    private Node root;

    public BST() {
        root = null;
    }

    // Method to insert an integer into the BST
    public void insert(int value) {
        root = insertRec(root, value);
    }

    // Recursive method to insert an integer
    private Node insertRec(Node root, int value) {
        if (root == null) {
            root = new Node(value);
            return root;
        }

        if (value < root.value) {
            root.left = insertRec(root.left, value);
        } else if (value > root.value) {
            root.right = insertRec(root.right, value);
        }

        return root;
    }

    // Method to search for an integer in the BST
    public boolean search(int value) {
        return searchRec(root, value);
    }

    // Recursive method to search for an integer
    private boolean searchRec(Node root, int value) {
        // Base case: root is null or value is found
        if (root == null) {
            return false; // Value not found
        }
        if (value == root.value) {
            return true; // Value found
        }

        // If the value is smaller than the root's value, search in the left subtree
        if (value < root.value) {
            return searchRec(root.left, value);
        } else { // If the value is greater than the root's value, search in the right subtree
            return searchRec(root.right, value);
        }
    }

    // Method to convert the BST to an array (in-order traversal)
    public List<Integer> bstToArray() {
        List<Integer> result = new ArrayList<>();
        inorderTraversal(root, result);
        return result;
    }

    // Helper method for in-order traversal
    private void inorderTraversal(Node root, List<Integer> result) {
        if (root != null) {
            inorderTraversal(root.left, result);
            result.add(root.value);
            inorderTraversal(root.right, result);
        }
    }
}