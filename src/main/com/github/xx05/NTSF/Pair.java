package com.github.xx05.NTSF;

class Pair<A, B> {
    private A first;
    private B second;

    public Pair(A first, B second) {
        this.first = first;
        this.second = second;
    }

    public A getFirst() {
        return first;
    }

    public void setFirst(A newValue) {
        first = newValue;
    }

    public B getSecond() {
        return second;
    }

    public void setSecond(B newValue) {
        second = newValue;
    }

    @Override
    public String toString() {
        return "(" + first + ", " + second + ")";
    }

    public boolean equals(Pair<?, ?> other) {
        if (other.first.getClass() != first.getClass())
            return false;
        if (other.second.getClass() != second.getClass())
            return false;

        if (!other.first.equals(first))
            return false;
        return other.second.equals(second);
    }
}
