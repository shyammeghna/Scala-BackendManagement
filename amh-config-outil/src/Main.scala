import com.groupaxis.groupsuite.amh.routing.application.services.ConfigTask

class Test(var arg1: String = "test",private var arg2: Int = 0, arg3: String = "t"){

  def this(arg1: String){
    this(arg1, 0)
  }

  def affichage(): Unit ={
    printf(arg1+" " + arg2 + " " + arg3)
  }
}

object Main {

  def main(args: Array[String]): Unit = {
    //ConfigTask.apply.importGrammar;
    val test = new Test("hi")
    test.affichage()
  }
}
