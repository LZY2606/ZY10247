package com.example.splice.solver;

import java.util.Objects;

/**
 * 精确非负分数（long 实现，自动约分）。所有路径流量/线性求解都使用精确算术，
 * 避免浮点容差把“并列分解”错误地判成一个唯一解。
 */
public final class Frac implements Comparable<Frac> {
    public static final Frac ZERO = new Frac(0, 1);
    public static final Frac ONE = new Frac(1, 1);

    private final long num;
    private final long den; // 恒为正

    public Frac(long num, long den) {
        if (den == 0) {
            throw new ArithmeticException("zero denominator");
        }
        if (den < 0) {
            num = -num;
            den = -den;
        }
        long g = gcd(Math.abs(num), den);
        if (g == 0) {
            g = 1;
        }
        this.num = num / g;
        this.den = den / g;
    }

    public static Frac of(long value) {
        return new Frac(value, 1);
    }

    private static long gcd(long a, long b) {
        while (b != 0) {
            long t = a % b;
            a = b;
            b = t;
        }
        return a;
    }

    public Frac add(Frac o) {
        return new Frac(num * o.den + o.num * den, den * o.den);
    }

    public Frac subtract(Frac o) {
        return new Frac(num * o.den - o.num * den, den * o.den);
    }

    public Frac multiply(Frac o) {
        return new Frac(num * o.num, den * o.den);
    }

    public Frac divide(Frac o) {
        if (o.num == 0) {
            throw new ArithmeticException("divide by zero");
        }
        return new Frac(num * o.den, den * o.num);
    }

    public Frac negate() {
        return new Frac(-num, den);
    }

    public boolean isZero() {
        return num == 0;
    }

    public boolean isNegative() {
        return num < 0;
    }

    public boolean isPositive() {
        return num > 0;
    }

    public boolean isOne() {
        return num == 1 && den == 1;
    }

    public long numerator() {
        return num;
    }

    public long denominator() {
        return den;
    }

    /** 渲染为稳定字符串（整数或 a/b）。 */
    public String toExactString() {
        return den == 1 ? Long.toString(num) : num + "/" + den;
    }

    public double toDouble() {
        return (double) num / den;
    }

    @Override
    public int compareTo(Frac o) {
        return Long.compare(num * o.den, o.num * den);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Frac f)) {
            return false;
        }
        return num == f.num && den == f.den;
    }

    @Override
    public int hashCode() {
        return Objects.hash(num, den);
    }

    @Override
    public String toString() {
        return toExactString();
    }
}
