package com.example.splice.svg;

import com.example.splice.splice.SpliceGraph;

/**
 * 剪接图 SVG：exon 为圆角矩形，junction 为带箭头的贝塞尔曲线；
 * 低可比对性边用虚线。节点上标注半开坐标 [start,end)。
 */
public final class GraphSvg {

    private GraphSvg() {
    }

    public static String render(SpliceGraph graph) {
        int step = 150;
        int x0 = 110;
        int baseY = 270;
        int altUp = 150;
        int altDown = 390;
        int width = x0 * 2 + step * (graph.nodes().size() - 1) + 40;
        int height = 500;

        StringBuilder sb = new StringBuilder();
        sb.append("<svg xmlns='http://www.w3.org/2000/svg' width='").append(width)
                .append("' height='").append(height)
                .append("' viewBox='0 0 ").append(width).append(' ').append(height)
                .append("' font-family='ui-sans-serif,system-ui,PingFang SC,Microsoft YaHei'>");
        sb.append("<defs>")
          .append("<marker id='arr' markerWidth='9' markerHeight='9' refX='8' refY='3' "
                + "orient='auto' markerUnits='strokeWidth'>")
          .append("<path d='M0,0 L8,3 L0,6 Z' fill='#3b4b66'/></marker>")
          .append("<marker id='arrLow' markerWidth='9' markerHeight='9' refX='8' refY='3' "
                + "orient='auto' markerUnits='strokeWidth'>")
          .append("<path d='M0,0 L8,3 L0,6 Z' fill='#b06a00'/></marker></defs>");
        sb.append("<rect width='100%' height='100%' fill='#f7f9fc'/>");

        sb.append("<text x='24' y='38' font-size='20' font-weight='700' fill='#1f2a44'>")
          .append(esc("剪接路径裁决台")).append("</text>");
        sb.append("<text x='24' y='62' font-size='13' fill='#54607a'>")
          .append(esc(graph.symbol())).append(" · ").append(esc(graph.contig()))
          .append(':').append(graph.spanStart()).append('-').append(graph.spanEnd())
          .append(" · 有效链 ").append(graph.effectiveStrand().code())
          .append(graph.strandFlipped() ? "（已镜像更正）" : "")
          .append(" · 坐标为半开 [start,end)")
          .append("</text>");

        // 坐标尺（始终基因组正向）
        int axisY = 92;
        sb.append("<line x1='24' y1='").append(axisY)
          .append("' x2='").append(width - 24).append("' y2='").append(axisY)
          .append("' stroke='#9aa6bd' stroke-width='1'/>");
        sb.append("<text x='24' y='").append(axisY - 6)
          .append("' font-size='11' fill='#7a869c'>基因组正方向 →</text>");

        for (SpliceGraph.Node node : graph.nodes()) {
            int x = x0 + (node.rank() - 1) * step;
            int y = yFor(node.rank(), baseY, altUp, altDown);
            sb.append("<g>");
            sb.append("<rect x='").append(x - 46).append("' y='").append(y - 24)
              .append("' width='92' height='48' rx='8' fill='#e8eefc' stroke='#5a76b8' stroke-width='1.5'/>");
            sb.append("<text x='").append(x).append("' y='").append(y - 4)
              .append("' text-anchor='middle' font-size='14' font-weight='700' fill='#24335a'>")
              .append(esc(node.exonId())).append("</text>");
            sb.append("<text x='").append(x).append("' y='").append(y + 14)
              .append("' text-anchor='middle' font-size='10' fill='#4a5572'>[")
              .append(node.start()).append(',').append(node.end()).append(")</text>");
            sb.append("</g>");
        }

        for (SpliceGraph.Edge e : graph.edges()) {
            int[] p1 = center(e.donorRank(), x0, step, baseY, altUp, altDown);
            int[] p2 = center(e.acceptorRank(), x0, step, baseY, altUp, altDown);
            boolean low = e.mappability() < 0.7;
            int colorY = (p1[1] + p2[1]) / 2;
            int midX = (p1[0] + p2[0]) / 2;
            int ctrlY = colorY - (p1[1] == baseY || p2[1] == baseY ? 70 : 30);
            String stroke = low ? "#b06a00" : "#3b4b66";
            String dash = low ? "stroke-dasharray='6 4' " : "";
            sb.append("<path d='M").append(p1[0] + 46).append(',').append(p1[1])
              .append(" Q").append(midX).append(',').append(ctrlY).append(' ')
              .append(p2[0] - 46).append(',').append(p2[1])
              .append("' fill='none' stroke='").append(stroke).append("' ")
              .append(dash).append("stroke-width='1.8' marker-end='url(#")
              .append(low ? "arrLow" : "arr").append(")'/>");
            sb.append("<text x='").append(midX).append("' y='").append(ctrlY + 16)
              .append("' text-anchor='middle' font-size='11' fill='").append(stroke)
              .append("'>").append(esc(e.junctionId()));
            if (low) {
                sb.append(" (map=").append(String.format("%.2f", e.mappability())).append(')');
            }
            sb.append("</text>");
        }

        // 图例
        sb.append("<g font-size='11' fill='#54607a'>")
          .append("<rect x='24' y='").append(height - 46)
          .append("' width='14' height='3' fill='#b06a00'/>")
          .append("<line x1='24' y1='").append(height - 44)
          .append("' x2='38' y2='").append(height - 44).append("' stroke='#b06a00' "
                + "stroke-dasharray='6 4'/>")
          .append("<text x='46' y='").append(height - 40).append("'>")
          .append("虚线：低可比对性（可排除）</text>")
          .append("<text x='24' y='").append(height - 18).append("'>")
          .append("边方向为转录 5'→3'；节点按转录顺序排布；端点为内含子半开区间")
          .append("</text></g>");

        sb.append("</svg>");
        return sb.toString();
    }

    private static int yFor(int rank, int baseY, int altUp, int altDown) {
        return switch (rank) {
            case 2, 5 -> altUp;
            case 3, 6 -> altDown;
            default -> baseY;
        };
    }

    private static int[] center(int rank, int x0, int step, int baseY, int altUp, int altDown) {
        int x = x0 + (rank - 1) * step;
        return new int[]{x, yFor(rank, baseY, altUp, altDown)};
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
