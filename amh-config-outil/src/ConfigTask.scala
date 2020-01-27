package com.groupaxis.groupsuite.amh.routing.application.services

import java.sql.{Connection, DriverManager, ResultSet}

import scala.io.Source

class configTask {
  val sourceKeyword = Source.fromFile("C:\\workspace_Xiaoyu\\AMHConfig\\RuleCriteria_Transaction.txt")
  val contentKeyword = sourceKeyword.getLines().toArray
  sourceKeyword.close()

  val conn_str = "jdbc:postgresql://localhost:5432/rpl"
  val conn = DriverManager.getConnection(conn_str,"rpldba","2Ypseatu")
  classOf[org.postgresql.Driver]

  val statement = conn.createStatement()

  def existParent(id : Int): Boolean ={
    val rs = statement.executeQuery(s"SELECT parent_id FROM public.rule_criteria_keyword WHERE id = '$id'")
    if(rs.next()){
      if(rs.getInt(1)!=0)
        return true;
    }
    return false;
  }

  def existedKeyword(keyword: String, path: String): Boolean={
    if(path.length==0){
      val rs = statement.executeQuery(s"SELECT id FROM public.rule_criteria_keyword WHERE keyword = '$keyword' AND path = '$path'")
      if(rs.next()){
        return true
      }else
        return false
    }else{
      val rs = statement.executeQuery(s"SELECT id FROM public.rule_criteria_keyword WHERE keyword = '$keyword' AND path = '$path'")
      if(rs.next()){
        return true
      }else
        return false
    }
  }

  def getId(keyword: String, path: String): Int = {
    val rs = statement.executeQuery(s"SELECT id FROM public.rule_criteria_keyword WHERE keyword = '$keyword' AND path = '$path'")
    if(rs.next()){
      return rs.getInt(1)
    }else
      return 0
  }

  def insertKeyword(keyword: String, parentId: Int, path: String, isNode: Boolean, hasChildren: Boolean)={
    val rs = statement.execute(s"INSERT INTO public.rule_criteria_keyword(keyword, parent_id, path, is_node, has_children) VALUES ('$keyword', '$parentId', '$path', '$isNode', '$hasChildren');")
  }

  def hasChildren(id: Int) = {
    val rs = statement.execute(s"UPDATE public.rule_criteria_keyword set has_children = true WHERE id = '$id'");
  }

  def importGrammar: Unit ={
    for(keyword <- contentKeyword){
      val keywordList = keyword.split("/")
      var isNew: Boolean = false;
      var path = ""
      var parentId = 0
      for (i <- 1 to keywordList.length){
        val key = keywordList(i-1)
        if(!isNew && this.existedKeyword(key,path)){
          //this.insertKeyword(key, parentId, path)
          if(i == keywordList.length){

          }else{
            val id = this.getId(key, path)
            this.hasChildren(id)
          }
        }else{
          isNew = true
          if(i == keywordList.length){
            this.insertKeyword(key, parentId, path, true, false)
          }else{
            this.insertKeyword(key, parentId, path, false, true)
          }
        }

        parentId = getId(key,path)
        path = path+key+"/"
      }
    }


  }

}

object ConfigTask{
  def apply = new configTask
}
