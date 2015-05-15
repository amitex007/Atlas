/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.metadata.bridge.hivelineage.hook;

import com.google.gson.Gson;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.hive.ql.lib.DefaultGraphWalker;
import org.apache.hadoop.hive.ql.lib.DefaultRuleDispatcher;
import org.apache.hadoop.hive.ql.lib.Dispatcher;
import org.apache.hadoop.hive.ql.lib.GraphWalker;
import org.apache.hadoop.hive.ql.lib.Node;
import org.apache.hadoop.hive.ql.lib.NodeProcessor;
import org.apache.hadoop.hive.ql.lib.NodeProcessorCtx;
import org.apache.hadoop.hive.ql.lib.Rule;
import org.apache.hadoop.hive.ql.parse.ASTNode;
import org.apache.hadoop.hive.ql.parse.HiveParser;
import org.apache.hadoop.hive.ql.parse.ParseDriver;
import org.apache.hadoop.hive.ql.parse.ParseException;
import org.apache.hadoop.hive.ql.parse.SemanticException;
import org.apache.hadoop.metadata.bridge.hivelineage.hook.HiveLineage.CreateColumns;
import org.apache.hadoop.metadata.bridge.hivelineage.hook.HiveLineage.GroupBy;
import org.apache.hadoop.metadata.bridge.hivelineage.hook.HiveLineage.QueryColumns;
import org.apache.hadoop.metadata.bridge.hivelineage.hook.HiveLineage.SourceTables;
import org.apache.hadoop.metadata.bridge.hivelineage.hook.HiveLineage.WhereClause;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Stack;

/**
 *
 * This class prints out the lineage info. It takes sql as input and prints
 * lineage info. Currently this prints only input and output tables for a given
 * sql. Later we can expand to add join tables etc.
 *
 */
public class HiveLineageInfo implements NodeProcessor {

    private final Log LOG = LogFactory.getLog(HiveLineageInfo.class.getName());
    public Map<Integer, String> queryMap;
    public Integer counter = 0;
    public HiveLineage hlb = new HiveLineage();
    ;
    public ArrayList<SourceTables> sourceTables;
    public ArrayList<QueryColumns> queryColumns;
    public ArrayList<GroupBy> groupBy;
    public ArrayList<WhereClause> whereClause;
    public ArrayList<CreateColumns> createColumns;

    //Main method to run tests and return json/gson feed from a query
    public static void main(String[] args) throws IOException, ParseException,
            SemanticException {

        String query = args[0];
        ConsoleAppender console = new ConsoleAppender(); //create appender
        //configure the appender
        String PATTERN = "%d [%p|%c|%C{1}] %m%n";
        console.setLayout(new PatternLayout(PATTERN));
        console.setThreshold(Level.DEBUG);
        console.activateOptions();
        //add appender to any Logger (here is root)
        Logger.getRootLogger().addAppender(console);
        LogManager.getRootLogger().setLevel(Level.DEBUG);

        HiveLineageInfo lep = new HiveLineageInfo();
        lep.getLineageInfo(query);
        Gson gson = new Gson();
        String jsonOut = gson.toJson(lep.getHLBean());
        System.out.println("GSON/JSON Generate :: " + jsonOut);

    }

    /**
     * @return Custom HiveLineageBean data to be passed to GSON parsert
     */


    public HiveLineage getHLBean() {
        return hlb;
    }

    /**
     * Implements the process method for the NodeProcessor interface.
     */
    public Object process(Node nd, Stack<Node> stack, NodeProcessorCtx procCtx,
                          Object... nodeOutputs) throws SemanticException {
        ASTNode pt = (ASTNode) nd;

	/*
    * Check the 1st-level children and do simple semantic checks: 1) CTLT and
	* CTAS should not coexists. 2) CTLT or CTAS should not coexists with column
	* list (target table schema). 3) CTAS does not support partitioning (for
	* now).
	*/

        switch (pt.getToken().getType()) {

            case HiveParser.TOK_FROM:
                LOG.debug("From Table Dump: " + pt.dump());
                fromTableDump(pt);
                break;

            case HiveParser.TOK_SELECT:
                LOG.debug("Column Dump: " + pt.dump());
                columnTableDump(pt);
                break;

            case HiveParser.TOK_SELECTDI:
                LOG.debug("Column Dump: " + pt.dump());
                columnTableDump(pt);
                break;

            case HiveParser.TOK_CREATETABLE:
                createColumns = new ArrayList<CreateColumns>();
                LOG.debug("CREATABLE DUMP: " + pt.dump());
                createTableDump(pt);
                break;

            case HiveParser.TOK_CREATEVIEW:
                createColumns = new ArrayList<CreateColumns>();
                LOG.debug("CREATEVIEW DUMP: " + pt.dump());
                createTableDump(pt);
                break;
	/*
	 * Currently disabling processing of WHERE and GROUPBY NO VALUE RIGHT NOW
	 *

    	case HiveParser.TOK_WHERE:
    		whereClause = new ArrayList<WhereClause>();
            LOG.debug("WHERE CLAUSE DUMP: "+pt.dump());
            whereDump(pt);
            this.hlb.setWhereClause(whereClause);
    	break;

    	case HiveParser.TOK_GROUPBY:
    		groupBy = new ArrayList<GroupBy>();
            LOG.debug("GROUPBY CLAUSE DUMP: "+pt.dump());
    		groupByDump(pt);
    		this.hlb.setGroupBy(groupBy);
    	break;

	*/
        }
        return null;
    }

    /**
     *  Walks the whereTree called by processWalker
     */
    public void whereDump(ASTNode nodeIn) {
        counter = 0;
        wdump(nodeIn);
    }

    /**
     *  Walks the Where Tree called by whereDump
     */
    private void wdump(ASTNode nodeIn) {
        boolean parseChild = true;
        if (nodeIn.getType() == HiveParser.TOK_TABLE_OR_COL) {
            WhereClause whreClse = hlb.new WhereClause();
            if (nodeIn.getParent().getText().equalsIgnoreCase(".")) {
                ASTNode checkOrAnd = (ASTNode) nodeIn.getParent().getParent().getChild(1)
                        .getParent().getParent();
                if (checkOrAnd.getType() == HiveParser.KW_AND ||
                        checkOrAnd.getType() == HiveParser.KW_OR) {
                    LOG.debug("WHERE:: " + checkOrAnd.getText());
                    whreClse.setColumnOperator(checkOrAnd.getText());
                }
                LOG.debug("Table Alias:: " + nodeIn.getChild(0).getText());
                whreClse.setTbAliasOrName(nodeIn.getChild(0).getText());
                LOG.debug("Delimiter:: " + nodeIn.getParent().getText());
                LOG.debug("Column:: " + nodeIn.getParent().getChild(1).getText());
                whreClse.setColumnName(nodeIn.getParent().getChild(1).getText());
                LOG.debug("Column Qualifer:: " +
                        nodeIn.getParent().getParent().getChild(1).getParent().getText());
                whreClse.setColumnOperator(
                        nodeIn.getParent().getParent().getChild(1).getParent().getText());
                LOG.debug("Column Value:: " + nodeIn.getParent().getParent().getChild(1).getText());
                whreClse.setColumnValue(nodeIn.getParent().getParent().getChild(1).getText());
            } else {
                ASTNode checkOrAnd = (ASTNode) nodeIn.getParent().getParent().getChild(1)
                        .getParent();
                if (checkOrAnd.getType() == HiveParser.KW_AND ||
                        checkOrAnd.getType() == HiveParser.KW_OR) {
                    LOG.debug("WHERE:: " + checkOrAnd.getText());
                    whreClse.setColumnOperator(checkOrAnd.getText());
                }
                LOG.debug("Column:: = " + nodeIn.getChild(0).getText());
                whreClse.setColumnName(nodeIn.getChild(0).getText());
                //LOG.info("Delimiter "+nodeIn.getParent().getText());
                LOG.debug("Column Qualifer:: " +
                        nodeIn.getParent().getChild(1).getParent().getText());
                whreClse.setColumnOperator(nodeIn.getParent().getChild(1).getParent().getText());
                LOG.debug("Column Value:: " + nodeIn.getParent().getChild(1).getText());
                whreClse.setColumnValue(nodeIn.getParent().getChild(1).getText());
            }
            whereClause.add(whreClse);
        }
        if (parseChild) {
            int childCount = nodeIn.getChildCount();
            if (childCount != 0) {
                for (int numr = 0; numr < childCount; numr++) {
                    wdump((ASTNode) nodeIn.getChild(numr));
                }
            }
        }
    }

    /**
     *  Walks the GroupByTree called by processWalker
     */
    public void groupByDump(ASTNode nodeIn) {
        counter = 0;
        gdump(nodeIn);
    }

    /**
     *  Walks the GroupBy Tree called by groupByDump
     */
    private void gdump(ASTNode nodeIn) {
        boolean parseChild = true;
        if (nodeIn.getType() == HiveParser.TOK_TABLE_OR_COL) {
            GroupBy grpBy = hlb.new GroupBy();
            ASTNode parentNode = (ASTNode) nodeIn.getParent();
            if (parentNode.getText().equalsIgnoreCase(".")) {
                LOG.debug("GroupBy TableAlias: " + nodeIn.getChild(0).getText());
                grpBy.setTbAliasOrName(nodeIn.getChild(0).getText());
                LOG.debug("GroupBy Column:: " + parentNode.getChild(1).getText());
                grpBy.setColumnName(parentNode.getChild(1).getText());
            } else {
                LOG.debug("GroupBy Column: " + nodeIn.getChild(0).getText());
                grpBy.setColumnName(nodeIn.getChild(0).getText());
            }
            groupBy.add(grpBy);
        }
        if (parseChild) {
            int childCount = nodeIn.getChildCount();
            if (childCount != 0) {
                for (int numr = 0; numr < childCount; numr++) {
                    gdump((ASTNode) nodeIn.getChild(numr));
                }
            }
        }
    }

    /**
     *  Walks the CreateTable Tree called by processWalker
     */

    public void createTableDump(ASTNode nodeIn) {
        counter = 0;
        if (nodeIn.getFirstChildWithType(HiveParser.TOK_TABNAME) != null &&
                nodeIn.getAncestor(HiveParser.TOK_WHERE) == null) {
            LOG.info("Create TableName:: " +
                    nodeIn.getFirstChildWithType(HiveParser.TOK_TABNAME).getText());
            if (nodeIn.getFirstChildWithType(HiveParser.TOK_TABNAME).getChildCount() == 2) {
                LOG.debug("To DataBaseName:: " +
                        nodeIn.getFirstChildWithType(HiveParser.TOK_TABNAME).getChild(0).getText());
                hlb.setDatabaseName(
                        nodeIn.getFirstChildWithType(HiveParser.TOK_TABNAME).getChild(0).getText());
                LOG.debug("To TableName:: " +
                        nodeIn.getFirstChildWithType(HiveParser.TOK_TABNAME).getChild(1).getText());
                hlb.setTableName(
                        nodeIn.getFirstChildWithType(HiveParser.TOK_TABNAME).getChild(1).getText());
            } else {
                LOG.debug("To TableName:: " +
                        nodeIn.getFirstChildWithType(HiveParser.TOK_TABNAME).getChild(0).getText());
                hlb.setTableName(
                        nodeIn.getFirstChildWithType(HiveParser.TOK_TABNAME).getChild(0).getText());
            }
        }
        if (nodeIn.getFirstChildWithType(HiveParser.TOK_TABLELOCATION) != null &&
                nodeIn.getAncestor(HiveParser.TOK_WHERE) == null) {
            LOG.debug("Create Table Location:: " +
                    nodeIn.getFirstChildWithType(HiveParser.TOK_TABLELOCATION).getText());
            hlb.setTableLocation(
                    nodeIn.getFirstChildWithType(HiveParser.TOK_TABLELOCATION).getChild(0)
                            .getText());
        }
        if (nodeIn.getFirstChildWithType(HiveParser.TOK_TABCOLLIST) != null &&
                nodeIn.getAncestor(HiveParser.TOK_WHERE) == null) {
            ctdump((ASTNode) nodeIn.getFirstChildWithType(HiveParser.TOK_TABCOLLIST).getParent());
            hlb.setCreateColumns(createColumns);
        }
    }

    /**
     *  Walks the CreateTable Tree called by createTableDump
     */
    private void ctdump(ASTNode nodeIn) {
        boolean parseChild = true;
        if (nodeIn.getType() == HiveParser.TOK_TABCOL) {
            CreateColumns crtClmns = hlb.new CreateColumns();
            LOG.debug("Create Column Name:: " + nodeIn.getChild(0).getText());
            crtClmns.setColumnName(nodeIn.getChild(0).getText());
            LOG.debug("Create Column Type:: " + nodeIn.getChild(1).getText());
            crtClmns.setColumnType(nodeIn.getChild(1).getText());
            createColumns.add(crtClmns);
        }
        if (parseChild) {
            int childCount = nodeIn.getChildCount();
            if (childCount != 0) {
                for (int numr = 0; numr < childCount; numr++) {
                    ctdump((ASTNode) nodeIn.getChild(numr));
                }
            }
        }
    }

    /**
     *  Walks the fromTable Tree called by processWalker
     */

    public void fromTableDump(ASTNode nodeIn) {
        counter = 0;
        ftdump(nodeIn);
    }

    /**
     *  Walks the fromTable Tree called by fromTableDump
     */
    private void ftdump(ASTNode nodeIn) {
        boolean parseChild = true;
        if (nodeIn.getType() == HiveParser.TOK_TABNAME &&
                nodeIn.getParent().getType() == HiveParser.TOK_TABREF &&
                nodeIn.getAncestor(HiveParser.TOK_WHERE) == null) {
            SourceTables hlbSbls = hlb.new SourceTables();
            if (nodeIn.getChildCount() == 2) {
                LOG.debug("From DBName:: " + nodeIn.getChild(0).getText());
                hlbSbls.setDatabaseName(nodeIn.getChild(0).getText());
                LOG.debug("From TableName:: " + nodeIn.getChild(1).getText());
                hlbSbls.setTableName(nodeIn.getChild(1).getText());
            } else {
                LOG.debug("From TableName:: " + nodeIn.getChild(0).getText());
                hlbSbls.setTableName(nodeIn.getChild(0).getText());

            }
            if (nodeIn.getType() == HiveParser.TOK_TABNAME &&
                    nodeIn.getParent().getChild(1) != null) {
                LOG.debug("From DB/Table Alias:: " + nodeIn.getParent().getChild(1).getText());
                hlbSbls.setTableAlias(nodeIn.getParent().getChild(1).getText());
            }
            sourceTables.add(hlbSbls);
        }
        if (parseChild) {
            int childCount = nodeIn.getChildCount();
            if (childCount != 0) {
                for (int numr = 0; numr < childCount; numr++) {
                    ftdump((ASTNode) nodeIn.getChild(numr));
                }
            }
        }
    }

    /**
     *  Walks the column Tree called by processWalker
     */

    public void columnTableDump(ASTNode nodeIn) {
        counter = 0;
        clmnTdump(nodeIn);
    }

    /**
     *  Walks the columnDump Tree called by columnTableDump
     */
    private void clmnTdump(ASTNode nodeIn) {
        boolean parseChild = true;
        if (nodeIn.getType() == HiveParser.TOK_TABLE_OR_COL &&
                nodeIn.getAncestor(HiveParser.TOK_SELEXPR) != null &&
                !(nodeIn.hasAncestor(HiveParser.TOK_WHERE))) {
            QueryColumns qclmns = hlb.new QueryColumns();
            if (nodeIn.getAncestor(HiveParser.TOK_FUNCTION) != null &&
                    nodeIn.getAncestor(HiveParser.TOK_SELEXPR) != null) {
                LOG.debug("Function Query:: " +
                        nodeIn.getAncestor(HiveParser.TOK_FUNCTION).getChild(0).getText());
                qclmns.setColumnFunction(
                        nodeIn.getAncestor(HiveParser.TOK_FUNCTION).getChild(0).getText());
            }
            if (nodeIn.getAncestor(HiveParser.TOK_FUNCTIONDI) != null &&
                    nodeIn.getAncestor(HiveParser.TOK_SELEXPR) != null) {
                LOG.debug("Function Distinct Query:: " +
                        nodeIn.getAncestor(HiveParser.TOK_FUNCTIONDI).getChild(0).getText());
                qclmns.setColumnDistinctFunction(
                        nodeIn.getAncestor(HiveParser.TOK_FUNCTIONDI).getChild(0).getText());
            }
            if (nodeIn.getParent().getText().equalsIgnoreCase(".")) {
                LOG.debug("Table Name/Alias:: " + nodeIn.getChild(0).getText());
                qclmns.setTbAliasOrName(nodeIn.getChild(0).getText());
                LOG.debug("Column:: " + nodeIn.getParent().getChild(1).getText());
                qclmns.setColumnName(nodeIn.getParent().getChild(1).getText());
                if (nodeIn.getAncestor(HiveParser.TOK_SELEXPR).getChild(1) != null) {
                    LOG.debug("Column Alias:: " +
                            nodeIn.getAncestor(HiveParser.TOK_SELEXPR).getChild(1).getText());
                    qclmns.setColumnAlias(
                            nodeIn.getAncestor(HiveParser.TOK_SELEXPR).getChild(1).getText());
                }
            } else {
                LOG.debug("Column:: " + nodeIn.getChild(0).getText());
                qclmns.setColumnName(nodeIn.getChild(0).getText());
                if ((nodeIn.getParent().getChild(1) != null &&
                        nodeIn.getParent().getChild(1).getType() != HiveParser.TOK_TABLE_OR_COL)) {
                    LOG.debug("Column Alias:: " + nodeIn.getParent().getChild(1).getText());
                    qclmns.setColumnAlias(nodeIn.getParent().getChild(1).getText());
                }
            }
            if (qclmns.getColumnName() != null) {
                queryColumns.add(qclmns);
            }
        }
        if (parseChild) {
            int childCount = nodeIn.getChildCount();
            if (childCount != 0) {
                for (int numr = 0; numr < childCount; numr++) {
                    clmnTdump((ASTNode) nodeIn.getChild(numr));
                }
            }
        }
    }

    /**
     * parses given query and gets the lineage info.
     *
     * @param query
     * @throws ParseException
     */
    public void getLineageInfo(String query) throws ParseException,
            SemanticException {

	 /*
	  * Get the AST tree
	  */
        ParseDriver pd = new ParseDriver();
        ASTNode tree = pd.parse(query);
        LOG.info("DUMP TREE: " + tree.dump());
        if (tree.getChild(0).getType() == HiveParser.TOK_DROPDATABASE) {
            hlb.setAction("drop_database");
        }
        if (tree.getChild(0).getType() == HiveParser.TOK_CREATEDATABASE) {
            hlb.setAction("create_database");
        }
        if (tree.getChild(0).getType() == HiveParser.TOK_CREATETABLE) {
            hlb.setAction("create_table");
        }
        if (tree.getChild(0).getType() == HiveParser.TOK_CREATEVIEW) {
            hlb.setAction("create_view");
        }
        if (tree.getChild(0).getType() == HiveParser.TOK_DROPTABLE) {
            hlb.setAction("drop_table");
        }
        if (tree.getChild(0).getType() == HiveParser.TOK_INSERT) {
            hlb.setAction("insert");
        }
        if (tree.getChild(0).getType() == HiveParser.TOK_INSERT_INTO) {
            hlb.setAction("insert_into");
        }
        if (tree.getChild(0).getType() == HiveParser.TOK_DROPVIEW) {
            hlb.setAction("drop_view");
        }
        if (tree.getChild(0).getType() == HiveParser.TOK_SHOWDATABASES) {
            hlb.setAction("show_databases");
        }
        if (tree.getChild(0).getType() == HiveParser.TOK_SHOWTABLES) {
            hlb.setAction("show_tables");
        }
        if (tree.getChild(0).getType() == HiveParser.TOK_ALTERVIEW_RENAME) {
            hlb.setAction("alter_view_rename");
        }
        if (tree.getChild(0).getType() == HiveParser.TOK_ALTERTABLE_RENAME) {
            hlb.setAction("alter_table_rename");
        }
        if (tree.getChild(0).getType() == HiveParser.TOK_ANALYZE) {
            hlb.setAction("analyze");
        }
        if (tree.getChild(0).getType() == HiveParser.TOK_QUERY) {
            hlb.setAction("select");
        }

        while ((tree.getToken() == null) && (tree.getChildCount() > 0)) {
            tree = (ASTNode) tree.getChild(0);
        }
        sourceTables = new ArrayList<SourceTables>();
        queryColumns = new ArrayList<QueryColumns>();


	 /*
	  * initialize Event Processor and dispatcher.
	  */

        // create a walker which walks the tree in a DFS manner while maintaining
        // the operator stack. The dispatcher
        // generates the plan from the operator tree
        Map<Rule, NodeProcessor> rules = new LinkedHashMap<Rule, NodeProcessor>();
        // The dispatcher fires the processor corresponding to the closest matching
        // rule and passes the context along
        Dispatcher disp = new DefaultRuleDispatcher(this, rules, null);
        GraphWalker ogw = new DefaultGraphWalker(disp);
        // Create a list of topop nodes
        ArrayList<Node> topNodes = new ArrayList<Node>();
        topNodes.add(tree);
        ogw.startWalking(topNodes, null);
        if (!(sourceTables.isEmpty())) {
            this.hlb.setSourceTables(sourceTables);
        }

        if (!(queryColumns.isEmpty())) {
            this.hlb.setQueryColumns(queryColumns);
        }
    }
}
