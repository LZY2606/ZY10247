package com.example.splice.solver;

import java.util.ArrayList;
import java.util.List;

/**
 * 精确有理线性方程组 A x = b（Gauss-Jordan 消元，RREF）。
 * 不使用浮点容差，确保并列稀疏分解不会被误判为唯一。
 * 兼容过定但一致的系统（行数可多于变量数）。
 */
public final class RationalSystem {

    private RationalSystem() {
    }

    /**
     * 求解 m x n 方程组。
     *
     * @return 唯一解；无解返回 null；存在自由变量（无穷多解）返回 null
     */
    public static Frac[] uniqueSolve(Frac[][] a, Frac[] b) {
        int m = a.length;
        int n = m == 0 ? 0 : a[0].length;
        Frac[][] mat = new Frac[m][n + 1];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                mat[i][j] = a[i][j] == null ? Frac.ZERO : a[i][j];
            }
            mat[i][n] = b[i] == null ? Frac.ZERO : b[i];
        }
        int cols = n;

        int pivotRow = 0;
        List<Integer> pivotCols = new ArrayList<>();
        for (int col = 0; col < cols; col++) {
            int sel = -1;
            for (int r = pivotRow; r < m; r++) {
                if (!mat[r][col].isZero()) {
                    sel = r;
                    break;
                }
            }
            if (sel == -1) {
                continue;
            }
            swap(mat, pivotRow, sel);
            Frac piv = mat[pivotRow][col];
            for (int j = 0; j <= cols; j++) {
                mat[pivotRow][j] = mat[pivotRow][j].divide(piv);
            }
            for (int r = 0; r < m; r++) {
                if (r == pivotRow) {
                    continue;
                }
                Frac factor = mat[r][col];
                if (factor.isZero()) {
                    continue;
                }
                for (int j = 0; j <= cols; j++) {
                    mat[r][j] = mat[r][j].subtract(factor.multiply(mat[pivotRow][j]));
                }
            }
            pivotCols.add(col);
            pivotRow++;
        }

        // 一致性：任何行的系数全为 0 时右端也必须为 0
        for (int r = 0; r < m; r++) {
            boolean allZero = true;
            for (int c = 0; c < cols; c++) {
                if (!mat[r][c].isZero()) {
                    allZero = false;
                    break;
                }
            }
            if (allZero && !mat[r][cols].isZero()) {
                return null;
            }
        }

        if (pivotCols.size() < n) {
            return null; // 自由变量 -> 非唯一
        }

        Frac[] x = new Frac[n];
        for (int i = 0; i < n; i++) {
            int col = pivotCols.get(i);
            x[col] = mat[i][cols];
        }
        return x;
    }

    private static void swap(Frac[][] mat, int r1, int r2) {
        Frac[] t = mat[r1];
        mat[r1] = mat[r2];
        mat[r2] = t;
    }
}
