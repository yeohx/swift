package com.luastar.swift.tools.func.mybatis;

import com.luastar.swift.base.utils.StrUtils;
import com.luastar.swift.tools.model.db.ColumnVO;
import com.luastar.swift.tools.model.db.TableVO;
import com.luastar.swift.tools.utils.BeetlUtils;
import com.luastar.swift.tools.utils.DataBaseUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class GenMybatis {

    private static final Logger logger = LoggerFactory.getLogger(GenMybatis.class);

    public static final String DB_POSTGRESQL = "postgresql";
    public static final String DB_MYSQL = "mysql";

    private static final String TEMP_MODEL = "/temp/mybatis/model.txt";
    private static final String TEMP_MAPPER = "/temp/mybatis/mapper.txt";
    private static final String TEMP_DAO = "/temp/mybatis/dao.txt";

    private String outDir;
    private String[] tableNames;
    private String modelPackageName;
    private String daoPackageName;
    private String mybatisRepPackageName;
    private boolean needSchema;
    private String dbName;
    private DataBaseUtils dbUtils;
    private BeetlUtils beetlUtils;

    public GenMybatis(String outDir,
                      String[] tableNames,
                      String modelPackageName,
                      String daoPackageName,
                      String mybatisRepPackageName,
                      String dbName,
                      String dbDriver,
                      String dbUrl,
                      String dbUsername,
                      String dbPassword) {

        this.outDir = outDir;
        this.tableNames = tableNames;
        this.modelPackageName = modelPackageName;
        this.daoPackageName = daoPackageName;
        this.mybatisRepPackageName = mybatisRepPackageName;
        this.dbName = dbName;
        this.dbUtils = new DataBaseUtils(dbDriver, dbUrl, dbUsername, dbPassword);
        this.beetlUtils = new BeetlUtils();
    }

    public void setNeedSchema(boolean needSchema) {
        this.needSchema = needSchema;
    }

    public void gen() {
        if (ArrayUtils.isEmpty(tableNames)) {
            logger.error("表不能为空！");
            return;
        }
        if (StringUtils.isEmpty(modelPackageName)) {
            logger.error("modelPackageName不能为空！");
            return;
        }
        if (StringUtils.isEmpty(daoPackageName)) {
            logger.error("daoPackageName不能为空！");
            return;
        }
        for (int i = 0; i < tableNames.length; i++) {
            gen_model(tableNames[i]);
            gen_dao(tableNames[i]);
            gen_xml(tableNames[i]);
        }
    }

    private void gen_model(String tableName) {
        TableVO tableVO = dbUtils.getDbTableInfo(tableName, needSchema);
        String className = getClassName(tableName);
        beetlUtils.setTemplate(TEMP_MODEL);
        beetlUtils.binding("classPackage", modelPackageName);
        beetlUtils.binding("className", className);
        beetlUtils.binding("colList", tableVO.getColumns());
        beetlUtils.toFile(outDir, className + ".java");
        logger.info("生成model成功，输出路径为：{}{}.java", outDir, className);
    }

    private void gen_dao(String tableName) {
        String className = getClassName(tableName);
        String daoName = className + "Dao";
        beetlUtils.setTemplate(TEMP_DAO);
        beetlUtils.binding("modelPackage", modelPackageName);
        beetlUtils.binding("daoPackage", daoPackageName);
        beetlUtils.binding("mybatisPackage", mybatisRepPackageName);
        beetlUtils.binding("daoName", daoName);
        beetlUtils.binding("className", className);
        beetlUtils.binding("classObjName", StrUtils.getFisrtCharLower(className));
        beetlUtils.toFile(outDir, daoName + ".java");
        logger.info("生成mapper成功，输出路径为：{}{}.java", outDir, daoName);
    }

    private void gen_xml(String tableName) {
        TableVO tableVO = dbUtils.getDbTableInfo(tableName, needSchema);
        String className = getClassName(tableName);
        beetlUtils.setTemplate(TEMP_MAPPER);
        if (DB_POSTGRESQL.equals(dbName)) {
            beetlUtils.binding("limit", "limit #{limit} offset #{start}");
        } else {
            beetlUtils.binding("limit", "limit #{start},#{limit}");
        }
        beetlUtils.binding("namespace", daoPackageName + "." + className + "Dao");
        beetlUtils.binding("mapName", StrUtils.getFisrtCharLower(className));
        beetlUtils.binding("className", className);
        beetlUtils.binding("tableName", tableName);
        beetlUtils.binding("pkColumn", tableVO.getPrimaryKey());
        beetlUtils.binding("colList", tableVO.getColumns());
        beetlUtils.binding("insertColList", getInsertRow(tableVO.getColumns(), "dbColumnName", 5));
        beetlUtils.binding("insertValList", getInsertRow(tableVO.getColumns(), "columnName", 5));
        beetlUtils.toFile(outDir, className + "Mapper.xml");
        logger.info("生成mapper成功，输出路径为：{}{}Mapper.xml", outDir, className);
    }

    private List<String> getInsertRow(List<ColumnVO> columnList, String prop, int rowNum) {
        if (columnList == null || columnList.isEmpty() || rowNum <= 0) {
            return new ArrayList<String>();
        }
        List<String> resultList = new ArrayList<String>();
        List<String> colList = new ArrayList<String>();
        for (int i = 0, l = columnList.size(); i < l; i++) {
            ColumnVO colVO = columnList.get(i);
            if (colVO.isPrikey()) {
                continue;
            }
            if (colList.size() > rowNum) {
                resultList.add(StringUtils.join(colList, ",") + ",");
                colList.clear();
            }
            if ("dbColumnName".equals(prop)) {
                colList.add(colVO.getDbColumnName());
            } else if ("columnName".equals(prop)) {
                colList.add("#{" + colVO.getColumnName() + "}");
            }
            if (i == l - 1) {
                resultList.add(StringUtils.join(colList, ","));
                colList.clear();
            }
        }
        return resultList;
    }

    private String getClassName(String tableName) {
        return StrUtils.getCamelCaseString(tableName, true);
    }

}
