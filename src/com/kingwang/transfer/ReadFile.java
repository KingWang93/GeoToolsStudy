package com.kingwang.transfer;

import java.io.File;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.FeatureSource;
import org.geotools.data.FeatureWriter;
import org.geotools.data.FileDataStoreFactorySpi;
import org.geotools.data.Transaction;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.geom.Point;

public class ReadFile {
    //转换线，多线和点shp文件(只是转换shp中的坐标，由WGS84坐标转换为火星坐标系)
    public static void tranferWGS84toMars(String srcshppath,String destshppath) throws Exception{
        File file = new File(srcshppath);
        Map<String, Object> map = new HashMap<>();
        map.put("url", file.toURI().toURL());

        DataStore srcdata = DataStoreFinder.getDataStore(map);
        ((ShapefileDataStore) srcdata).setCharset(Charset.forName("GBK"));//国内的计算机默认编码格式为GBK，所以如果不进行设置的话，就会出现乱码的现象
        String typeName = srcdata.getTypeNames()[0];

        FeatureSource<SimpleFeatureType, SimpleFeature> source = srcdata
                .getFeatureSource(typeName);
        Filter filter = Filter.INCLUDE; // ECQL.toFilter("BBOX(THE_GEOM, 10,20,30,40)")

        FeatureCollection<SimpleFeatureType, SimpleFeature> collection = source.getFeatures(filter);
      //创建目标shape文件对象  
        Map<String, Serializable> params = new HashMap<String, Serializable>();  
        FileDataStoreFactorySpi factory = new ShapefileDataStoreFactory();  
        params.put(ShapefileDataStoreFactory.URLP.key, new File(destshppath).toURI().toURL());  
        ShapefileDataStore ds = (ShapefileDataStore) factory.createNewDataStore(params);  
        ((ShapefileDataStore) ds).setCharset(Charset.forName("GBK"));
        // 设置属性  
        SimpleFeatureSource fs = srcdata.getFeatureSource(srcdata.getTypeNames()[0]);  
        //下面这行还有其他写法，根据源shape文件的simpleFeatureType可以不用retype，而直接用fs.getSchema设置  
        ds.createSchema(SimpleFeatureTypeBuilder.retype(fs.getSchema(), DefaultGeographicCRS.WGS84));  

        //设置writer  
        FeatureWriter<SimpleFeatureType, SimpleFeature> writer = ds.getFeatureWriter(ds.getTypeNames()[0], Transaction.AUTO_COMMIT);
        try (FeatureIterator<SimpleFeature> features = collection.features()) {
            while (features.hasNext()) {
                SimpleFeature feature = features.next();
                List<Object> list=feature.getAttributes();
                Object obj=list.get(0);
                if(obj instanceof LineString||obj instanceof MultiLineString){
                    Geometry line=((Geometry) obj);
                    int parts=line.getNumGeometries();
                    for(int i=0;i<parts;i++){
                        LineString l=(LineString)line.getGeometryN(i);
                        for(int j=0,num=l.getNumPoints();j<num;j++){
                            Coordinate coor=l.getCoordinateN(j);
                            double[] xy=WGS_Encrypt.WGS2Mars(coor.y,coor.x);
                            Coordinate newcoor=new Coordinate(xy[1],xy[0]);
                            coor.setCoordinate(newcoor);
                        }
                    }
                }else if(obj instanceof Point){
                    Point pt=(Point)obj;
                    Coordinate coor=pt.getCoordinate();
                    double[] xy=WGS_Encrypt.WGS2Mars(coor.y,coor.x);
                    coor.setCoordinate(new Coordinate(xy[1], xy[0]));
                }
                SimpleFeature fNew = writer.next();
                fNew.setAttributes(feature.getAttributes());  
                writer.write();
            }
        }
        writer.close();
        ds.dispose();  
        srcdata.dispose(); 
    }

    public static void main(String[] args) throws Exception {
        tranferWGS84toMars("H:\\data\\道路空间数据-单线-WGS84坐标系\\compare4.shp","C:\\Users\\KingWang\\Desktop\\dest\\compare4.shp");
//        tranferWGS84toMars("C:\\Users\\KingWang\\Desktop\\最新数据\\空间数据\\路段.shp","C:\\Users\\KingWang\\Desktop\\dest\\路段.shp");
//        tranferWGS84toMars("C:\\Users\\KingWang\\Desktop\\最新数据\\空间数据\\路口.shp","C:\\Users\\KingWang\\Desktop\\dest\\路口.shp");
    }
}