package io.github.qmjy.mapserver.util;

import io.github.qmjy.mapserver.model.dto.GeometryPointDTO;
import org.geotools.api.referencing.operation.MathTransform;
import org.geotools.api.referencing.operation.Matrix;
import org.geotools.api.referencing.operation.TransformException;
import org.geotools.geometry.Position2D;
import org.geotools.referencing.operation.matrix.MatrixFactory;
import org.geotools.referencing.operation.matrix.XMatrix;
import org.geotools.referencing.operation.transform.ProjectiveTransform;

import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class ImageGeoreferencer {

    // 图片的宽度和高度(像素)
    private final int imageWidth;
    private final int imageHeight;

    // 地理四边形的四个角点(按左上、右上、右下、左下顺序)
    private final Position2D[] geoQuadrilateral;

    // 透视变换对象
    private MathTransform imageToGeoTransform;
    private MathTransform geoToImageTransform;

    /**
     * 构造函数
     *
     * @param imageWidth       图片宽度(像素)
     * @param imageHeight      图片高度(像素)
     * @param geoQuadrilateral 地理四边形的四个角点(按左上、右上、右下、左下顺序)
     */
    public ImageGeoreferencer(int imageWidth, int imageHeight, Position2D[] geoQuadrilateral) {
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.geoQuadrilateral = geoQuadrilateral.clone();

        // 初始化透视变换
        initializeTransforms();
    }

    private void initializeTransforms() {
        // 图片四边形的四个角点(像素坐标)
        Position2D[] imageCorners = new Position2D[]{
                new Position2D(0, 0),                  // 左上
                new Position2D(imageWidth, 0),            // 右上
                new Position2D(imageWidth, imageHeight),     // 右下
                new Position2D(0, imageHeight)            // 左下
        };

        // 计算从图片坐标到地理坐标的透视变换
        imageToGeoTransform = createPerspectiveTransform(imageCorners, geoQuadrilateral);

        // 计算从地理坐标到图片坐标的透视变换(逆变换)
        try {
            geoToImageTransform = imageToGeoTransform.inverse();
        } catch (Exception e) {
            throw new RuntimeException("无法计算逆变换", e);
        }
    }

    /**
     * 创建透视变换
     *
     * @param src 源四边形(图片坐标)
     * @param dst 目标四边形(地理坐标)
     * @return 透视变换对象
     */
    private MathTransform createPerspectiveTransform(Position2D[] src, Position2D[] dst) {
        // 透视变换需要解一个8参数的线性方程组
        Matrix matrix = calculatePerspectiveMatrix(src, dst);
        return ProjectiveTransform.create(matrix);
    }

    /**
     * 计算透视变换矩阵
     */
    private Matrix calculatePerspectiveMatrix(Position2D[] src, Position2D[] dst) {
        // 使用GeoTools的MatrixFactory创建矩阵
        XMatrix matrix = MatrixFactory.create(3, 3);

        // 原始计算过程保持不变
        double[][] A = new double[8][8];
        double[] b = new double[8];

        for (int i = 0; i < 4; i++) {
            double x = src[i].getCoordinate()[0];
            double y = src[i].getCoordinate()[1];
            double u = dst[i].getCoordinate()[0];
            double v = dst[i].getCoordinate()[1];

            A[2 * i][0] = x;
            A[2 * i][1] = y;
            A[2 * i][2] = 1;
            A[2 * i][3] = 0;
            A[2 * i][4] = 0;
            A[2 * i][5] = 0;
            A[2 * i][6] = -x * u;
            A[2 * i][7] = -y * u;
            b[2 * i] = u;

            A[2 * i + 1][0] = 0;
            A[2 * i + 1][1] = 0;
            A[2 * i + 1][2] = 0;
            A[2 * i + 1][3] = x;
            A[2 * i + 1][4] = y;
            A[2 * i + 1][5] = 1;
            A[2 * i + 1][6] = -x * v;
            A[2 * i + 1][7] = -y * v;
            b[2 * i + 1] = v;
        }

        double[] h = solveLinearSystem(A, b);

        // 使用setElement方法填充矩阵值
        matrix.setElement(0, 0, h[0]);
        matrix.setElement(0, 1, h[1]);
        matrix.setElement(0, 2, h[2]);
        matrix.setElement(1, 0, h[3]);
        matrix.setElement(1, 1, h[4]);
        matrix.setElement(1, 2, h[5]);
        matrix.setElement(2, 0, h[6]);
        matrix.setElement(2, 1, h[7]);
        matrix.setElement(2, 2, 1);

        return matrix;
    }

    /**
     * 解线性方程组 (高斯消元法)
     */
    private double[] solveLinearSystem(double[][] A, double[] b) {
        int n = b.length;
        double[] x = new double[n];

        // 前向消元
        for (int p = 0; p < n; p++) {
            // 找主元行
            int max = p;
            for (int i = p + 1; i < n; i++) {
                if (Math.abs(A[i][p]) > Math.abs(A[max][p])) {
                    max = i;
                }
            }

            // 交换行
            double[] temp = A[p];
            A[p] = A[max];
            A[max] = temp;
            double t = b[p];
            b[p] = b[max];
            b[max] = t;

            // 消元
            for (int i = p + 1; i < n; i++) {
                double alpha = A[i][p] / A[p][p];
                b[i] -= alpha * b[p];
                for (int j = p; j < n; j++) {
                    A[i][j] -= alpha * A[p][j];
                }
            }
        }

        // 回代
        for (int i = n - 1; i >= 0; i--) {
            double sum = 0.0;
            for (int j = i + 1; j < n; j++) {
                sum += A[i][j] * x[j];
            }
            x[i] = (b[i] - sum) / A[i][i];
        }

        return x;
    }

    /**
     * 将图片坐标转换为地理坐标
     *
     * @param imagePoint 图片坐标(像素)
     * @return 对应的地理坐标
     */
    public GeometryPointDTO transformImageToGeo(Position2D imagePoint) throws TransformException {
        Position2D result = new Position2D();
        imageToGeoTransform.transform(imagePoint, result);
        return new GeometryPointDTO(result);
    }

    /**
     * 批量将图片坐标转换为地理坐标
     *
     * @param imagePoints 图片坐标数组(像素)
     * @return 对应的地理坐标数组
     */
    public Map<String, GeometryPointDTO> transformImageToGeo(List<Position2D> imagePoints) throws TransformException {
        Map<String, GeometryPointDTO> results = new HashMap<>(imagePoints.size());
        for (Position2D point : imagePoints) {
            results.put((int) point.getX() + "-" + (int) point.getY(), transformImageToGeo(point));
        }
        return results;
    }

    /**
     * 将地理坐标转换为图片坐标
     *
     * @param geoPoint 地理坐标
     * @return 对应的图片坐标(像素)
     */
    public Point2D transformGeoToImage(Position2D geoPoint) throws TransformException {
        Position2D result = new Position2D();
        geoToImageTransform.transform(geoPoint, result);
        return result;
    }

    /**
     * 批量将地理坐标转换为图片坐标
     *
     * @param geoPoints 地理坐标数组
     * @return 对应的图片坐标数组(像素)
     */
    public Map<String, Point2D> transformGeoToImage(List<Position2D> geoPoints) throws TransformException {
        Map<String, Point2D> results = new HashMap<>(geoPoints.size());
        for (Position2D point : geoPoints) {
            results.put(point.getX() + "" + point.getY(), transformGeoToImage(point));
        }
        return results;
    }
}