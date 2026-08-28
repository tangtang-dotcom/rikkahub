package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import me.rerere.rikkahub.data.terminal.AndroidRootTerminalController
import org.json.JSONObject
import java.io.File

internal class StructuredColorOsMemorySource(private val context:Context,private val root:AndroidRootTerminalController){
    fun execute(tool:String,args:JSONObject):String=synchronized(lock){
        val operation=when(tool){"search_coloros_memories"->"search";"search_saved_places"->"places";"search_personal_orders"->"orders";else->return@synchronized error("TOOL_UNAVAILABLE")}
        val user=context.dataDir.parentFile?.name?.toIntOrNull()?:return@synchronized error("COLOROS_MEMORY_USER_UNAVAILABLE")
        val source="/data/user/$user/com.oplus.aimemory/databases/ai_memory";val target=runCatching{File.createTempFile("rikka-coloros-memory-",".db",context.cacheDir)}.getOrNull()?:return@synchronized error("COLOROS_MEMORY_SNAPSHOT_TARGET_UNAVAILABLE")
        try{
            val q={v:String->"'"+v.replace("'","'\\''")+"'"};val command=buildString{append("[ -f ${q(source)} ] || exit 21; [ ! -L ${q(source)} ] || exit 22; [ \"\\$(stat -c %s ${q(source)})\" -le 67108864 ] || exit 23; cp ${q(source)} ${q(target.absolutePath)} || exit 24; ");listOf("-wal","-shm","-journal").forEach{suffix->append("if [ -f ${q(source+suffix)} ]; then cp ${q(source+suffix)} ${q(target.absolutePath+suffix)} || exit 27; fi; ")}}
            val result=root.executeSync(command,timeoutMs=15_000);if(result.exitCode!=0||result.timedOut)return@synchronized error(if(result.timedOut)"COLOROS_MEMORY_SNAPSHOT_TIMEOUT" else "COLOROS_MEMORY_UNAVAILABLE")
            return@synchronized runCatching{SQLiteDatabase.openDatabase(target.absolutePath,null,SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use{ColorOsMemoryDatabaseQuery.execute(it,operation,args)}}.getOrElse{error("COLOROS_MEMORY_QUERY_FAILED")}
        }finally{listOf("","-wal","-shm","-journal").forEach{File(target.absolutePath+it).delete()}}
    }
    private fun error(code:String)=JSONObject().put("ok",false).put("code",code).toString()
    companion object{private val lock=Any()}
}
