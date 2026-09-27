package com.example.skilkavach.ar

import android.graphics.*
import android.opengl.GLES20.*
import android.opengl.GLUtils
import android.opengl.Matrix
import java.nio.FloatBuffer
import kotlin.math.*

/** Original runtime geometry and vector artwork; no downloaded or placeholder GLB assets. */
class EquipmentRenderer {
    private data class Mesh(val positions: FloatBuffer, val normals: FloatBuffer)
    private val metal = floatArrayOf(.55f,.62f,.70f,1f)
    private val dark = floatArrayOf(.055f,.07f,.095f,1f)
    private val red = floatArrayOf(.72f,.035f,.045f,1f)
    private val white = floatArrayOf(.94f,.96f,.98f,1f)
    private val yellow = floatArrayOf(.98f,.65f,.045f,1f)
    private var program = 0
    private var decalProgram = 0
    private val textures = mutableMapOf<String,Int>()
    private val box = boxMesh()
    private val round = lathe(listOf(0f to 1f, 1f to 1f))
    private val bottle = lathe(listOf(0f to .70f,.02f to .88f,.07f to 1f,.78f to 1f,.86f to .94f,.93f to .65f,1f to .30f))
    private val ring = torus()
    private val quad = TrainingRenderer.floats(floatArrayOf(-.5f,-.5f,0f,.5f,-.5f,0f,-.5f,.5f,0f,.5f,.5f,0f))
    private val uv = TrainingRenderer.floats(floatArrayOf(0f,1f,1f,1f,0f,0f,1f,0f))
    fun initialize() {
        textures.clear()
        program = shaderProgram(
            "attribute vec3 p; attribute vec3 n; uniform mat4 mvp; uniform mat3 normalMatrix; varying vec3 normal; void main(){gl_Position=mvp*vec4(p,1.);normal=normalize(normalMatrix*n);}",
            "precision mediump float; varying vec3 normal; uniform vec4 color; uniform float gloss; void main(){vec3 n=normalize(normal);vec3 l=normalize(vec3(-.5,.9,1.));float d=max(dot(n,l),0.);float spec=pow(max(dot(n,normalize(l+vec3(0.,.2,1.))),0.),40.);float rim=pow(1.-abs(n.z),3.);gl_FragColor=vec4(color.rgb*(.38+.62*d)+vec3(spec*gloss+rim*.045),color.a);}")
        decalProgram = shaderProgram("attribute vec3 p; attribute vec2 uv; uniform mat4 mvp; varying vec2 t; void main(){gl_Position=mvp*vec4(p,1.);t=uv;}", "precision mediump float; varying vec2 t; uniform sampler2D artwork; void main(){gl_FragColor=texture2D(artwork,t);}")
        listOf("exit","stop","alarm","label","detector","permit","gauge","sweep","hazard","buddy","ventilation").forEach { key ->
            val bitmap=artwork(key)
            val id=IntArray(1);glGenTextures(1,id,0);glBindTexture(GL_TEXTURE_2D,id[0])
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR_MIPMAP_LINEAR)
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE)
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE)
            GLUtils.texImage2D(GL_TEXTURE_2D,0,bitmap,0);glGenerateMipmap(GL_TEXTURE_2D);bitmap.recycle();textures[key]=id[0]
        }
    }
    fun draw(id:String, base:FloatArray, pinPull:Float = 0f) {
        fun part(mesh:Mesh,c:FloatArray,x:Float,y:Float,z:Float,sx:Float,sy:Float,sz:Float,gloss:Float=.12f,angle:Float=0f) {
            val local=FloatArray(16);Matrix.setIdentityM(local,0);Matrix.translateM(local,0,x,y,z);Matrix.rotateM(local,0,angle,1f,0f,0f);Matrix.scaleM(local,0,sx,sy,sz)
            val mvp=FloatArray(16);Matrix.multiplyMM(mvp,0,base,0,local,0)
            val inverse=FloatArray(16);Matrix.invertM(inverse,0,local,0)
            val normal=floatArrayOf(inverse[0],inverse[4],inverse[8],inverse[1],inverse[5],inverse[9],inverse[2],inverse[6],inverse[10])
            glUseProgram(program);attribute(program,"p",mesh.positions,3);attribute(program,"n",mesh.normals,3)
            glUniformMatrix4fv(glGetUniformLocation(program,"mvp"),1,false,mvp,0);glUniformMatrix3fv(glGetUniformLocation(program,"normalMatrix"),1,false,normal,0)
            glUniform4fv(glGetUniformLocation(program,"color"),1,c,0);glUniform1f(glGetUniformLocation(program,"gloss"),gloss)
            glDrawArrays(GL_TRIANGLES,0,mesh.positions.capacity()/3)
        }
        fun panel(key:String,x:Float,y:Float,z:Float,w:Float,h:Float) {
            val m=base.copyOf();Matrix.translateM(m,0,x,y,z);Matrix.scaleM(m,0,w,h,1f)
            glUseProgram(decalProgram);attribute(decalProgram,"p",quad,3);attribute(decalProgram,"uv",uv,2)
            glUniformMatrix4fv(glGetUniformLocation(decalProgram,"mvp"),1,false,m,0)
            glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,textures.getValue(key));glUniform1i(glGetUniformLocation(decalProgram,"artwork"),0)
            glDrawArrays(GL_TRIANGLE_STRIP,0,4)
        }
        // Weighted mounting plinth and fine metal rim ground each exhibit.
        part(round,dark,0f,0f,0f,.145f,.016f,.12f)
        part(round,metal,0f,.016f,0f,.143f,.004f,.118f,.55f)
        when(id) {
            "extinguisher", "pin" -> {
                part(bottle,red,0f,.025f,0f,.077f,.35f,.077f,.42f)
                part(round,dark,0f,.026f,0f,.079f,.026f,.079f)
                part(round,metal,0f,.365f,0f,.025f,.032f,.025f,.75f)
                part(box,dark,.018f,.409f,0f,.12f,.018f,.025f,.4f)
                part(box,red,.018f,.434f,0f,.12f,.013f,.024f,.3f)
                // Withdraw the complete safety pin from the valve, then lift it clear.
                val pull = pinPull.coerceIn(0f,1f)
                val offset = pull * .18f
                val lift = ((pull-.65f)/.35f).coerceIn(0f,1f)*.055f
                part(box,metal,-.025f-offset,.396f+lift,0f,.08f,.007f,.007f,.85f)
                part(ring,metal,-.065f-offset,.396f+lift,0f,.026f,.026f,.026f,.8f)
                // Curved black hose with brass couplings and flared nozzle.
                for(i in 0..13) { val t=i/13f;part(round,dark,.048f+.068f*sin(t*PI).toFloat(),.355f-t*.23f,-.012f,.011f,.025f,.011f) }
                part(bottle,dark,.09f,.075f,0f,.023f,.09f,.023f,.18f)
                panel("label",0f,.207f,.078f,.104f,.15f)
                part(round,metal,0f,.373f,.029f,.024f,.018f,.024f,.8f,90f)
                panel("gauge",0f,.373f,.051f,.040f,.040f)
            }
            "handle" -> {
                part(round,metal,0f,.02f,0f,.037f,.20f,.037f,.75f)
                part(box,dark,0f,.26f,0f,.25f,.032f,.056f,.35f)
                part(box,red,0f,.32f,0f,.25f,.025f,.056f,.4f)
                for(x in listOf(-.09f,.09f)) part(round,metal,x,.26f,.031f,.009f,.008f,.009f,.8f,90f)
            }
            "detector" -> {
                part(box,dark,0f,.19f,0f,.20f,.32f,.075f)
                part(box,yellow,0f,.19f,.014f,.178f,.295f,.07f,.15f)
                panel("detector",0f,.225f,.051f,.148f,.145f)
                for(x in listOf(-.05f,0f,.05f)) part(round,dark,x,.09f,.047f,.015f,.012f,.015f,.3f,90f)
                for(i in 0..5) part(box,dark,-.06f+i*.024f,.325f,.051f,.011f,.017f,.004f)
            }
            "alarm" -> {
                part(box,metal,0f,.22f,0f,.26f,.30f,.085f,.6f)
                part(box,red,0f,.22f,.02f,.245f,.285f,.075f,.25f)
                panel("alarm",0f,.225f,.059f,.222f,.256f)
                for(x in listOf(-.101f,.101f)) for(y in listOf(.1f,.34f)) part(round,metal,x,y,.062f,.007f,.005f,.007f,.7f,90f)
            }
            "permit" -> {
                part(box,dark,0f,.235f,0f,.24f,.36f,.018f)
                panel("permit",0f,.235f,.011f,.215f,.328f)
                part(box,metal,0f,.405f,.018f,.10f,.032f,.014f,.6f)
            }
            "ppe" -> {
                part(bottle,yellow,0f,.20f,0f,.12f,.14f,.10f,.3f)
                part(round,yellow,0f,.20f,0f,.14f,.016f,.115f,.2f)
                part(box,dark,0f,.155f,.06f,.20f,.065f,.045f)
                for(x in listOf(-.052f,.052f)) part(box,floatArrayOf(.16f,.42f,.52f,1f),x,.16f,.088f,.084f,.044f,.012f,.8f)
                part(bottle,white,0f,.04f,.015f,.075f,.09f,.046f)
                part(round,dark,0f,.08f,.067f,.024f,.016f,.024f,.2f,90f)
            }
            "buddy" -> {
                part(bottle,yellow,0f,.14f,0f,.065f,.15f,.043f)
                part(bottle,white,0f,.30f,0f,.047f,.078f,.047f)
                for(x in listOf(-.036f,.036f)) part(round,dark,x,.02f,0f,.025f,.14f,.025f)
                for(x in listOf(-.09f,.09f)) part(round,yellow,x,.16f,0f,.022f,.12f,.022f)
                part(box,white,0f,.235f,.044f,.125f,.014f,.005f)
                panel("buddy",0f,.19f,.05f,.065f,.045f)
            }
            else -> {
                val key=when(id){"exit"->"exit";"stop","isolation","hazard"->"stop";"sweep"->"sweep";"ventilation","ventilate"->"ventilation";else->"hazard"}
                val wide=id=="exit"
                val w=if(wide).37f else .27f
                val h=if(wide).20f else .27f
                part(round,metal,0f,.02f,0f,.012f,.14f,.012f,.7f)
                part(box,dark,0f,.27f,0f,w+.018f,h+.018f,.035f,.3f)
                part(box,metal,0f,.27f,.018f,w+.008f,h+.008f,.009f,.7f)
                panel(key,0f,.27f,.024f,w,h)
            }
        }
    }
    private fun artwork(key:String):Bitmap {
        val bmp=Bitmap.createBitmap(1024,1024,Bitmap.Config.ARGB_8888);val c=Canvas(bmp)
        val p=Paint(Paint.ANTI_ALIAS_FLAG);p.typeface=Typeface.create("sans-serif",Typeface.BOLD)
        fun rect(x:Float,y:Float,w:Float,h:Float,color:Int,r:Float=0f){p.color=color;c.drawRoundRect(x,y,x+w,y+h,r,r,p)}
        fun text(t:String,y:Float,size:Float,color:Int=Color.WHITE){p.color=color;p.textSize=size;p.textAlign=Paint.Align.CENTER;c.drawText(t,512f,y,p)}
        val navy=Color.rgb(20,31,46);val green=Color.rgb(0,112,66);val scarlet=Color.rgb(185,22,35)
        c.drawColor(Color.rgb(246,248,250))
        when(key){
            "exit"->{c.drawColor(green);rect(25f,25f,974f,974f,Color.WHITE,25f);rect(44f,44f,936f,936f,green,15f);text("EMERGENCY",170f,78f);text("EXIT",365f,182f)
                rect(145f,470f,165f,350f,Color.WHITE);rect(170f,495f,115f,325f,green)
                p.color=Color.WHITE;c.drawCircle(441f,509f,43f,p);p.strokeWidth=48f;p.strokeCap=Paint.Cap.ROUND
                c.drawLine(443f,581f,390f,681f,p);c.drawLine(425f,607f,550f,637f,p);c.drawLine(426f,615f,328f,583f,p);c.drawLine(390f,681f,470f,788f,p);c.drawLine(392f,681f,300f,757f,p)
                rect(600f,603f,215f,58f,Color.WHITE);val arrow=Path();arrow.moveTo(755f,525f);arrow.lineTo(870f,632f);arrow.lineTo(755f,742f);arrow.close();c.drawPath(arrow,p);text("KEEP CLEAR",934f,62f)
            }
            "stop"->{c.drawColor(navy);val path=Path();for(i in 0..7){val angle=(22.5+i*45)*PI/180;val x=512+440*cos(angle).toFloat();val y=488+440*sin(angle).toFloat();if(i==0)path.moveTo(x,y)else path.lineTo(x,y)};path.close();p.color=Color.WHITE;c.drawPath(path,p);c.save();c.scale(.94f,.94f,512f,488f);p.color=scarlet;c.drawPath(path,p);c.restore();text("STOP",560f,210f);text("CHECK BEFORE ENTRY",976f,47f,navy)}
            "alarm"->{c.drawColor(scarlet);text("FIRE",188f,140f);text("ALARM",326f,128f);rect(75f,405f,874f,390f,Color.WHITE,26f);rect(102f,432f,820f,336f,navy,15f);text("PRESS HERE",620f,88f);text("TRAINING UNIT",914f,55f)}
            "label"->{text("CO₂",225f,185f,scarlet);text("EXTINGUISHER",325f,71f,navy);rect(80f,375f,864f,8f,scarlet);text("TRAINING MODEL",500f,68f,navy);for((i,t)in listOf("1  PULL THE PIN","2  AIM AT BASE","3  SQUEEZE HANDLE","4  SWEEP SIDEWAYS").withIndex())text(t,620f+i*90,49f,navy)}
            "gauge"->{c.drawColor(Color.WHITE);p.color=navy;p.style=Paint.Style.STROKE;p.strokeWidth=35f;c.drawCircle(512f,512f,450f,p);p.style=Paint.Style.FILL;for(i in 0..10){val a=(135+i*27)*PI/180;p.strokeWidth=13f;c.drawLine(512+350*cos(a).toFloat(),512+350*sin(a).toFloat(),512+410*cos(a).toFloat(),512+410*sin(a).toFloat(),p)};p.color=scarlet;p.strokeWidth=26f;c.drawLine(512f,512f,660f,260f,p);c.drawCircle(512f,512f,43f,p);text("SIM",760f,82f,navy)}
            "detector"->{c.drawColor(navy);text("MULTI-GAS",135f,96f);rect(48f,190f,928f,640f,Color.rgb(193,226,204),18f);text("O₂    --.- %",355f,109f,navy);text("LEL   --- %",515f,109f,navy);text("CO    --- ppm",675f,94f,navy);text("SIMULATION • NO SENSOR",946f,48f)}
            "permit"->{text("ENTRY PERMIT",135f,95f,navy);text("TRAINING COPY",225f,62f,scarlet);for((i,t)in listOf("AREA / EQUIPMENT","ISOLATION CHECK","ATMOSPHERE TEST","ATTENDANT READY","SUPERVISOR REVIEW").withIndex()){text(t,345f+i*115,48f,navy);rect(85f,370f+i*115,850f,5f,Color.LTGRAY)};text("REVIEW BEFORE ENTRY",968f,44f,navy)}
            "sweep"->{c.drawColor(navy);text("SWEEP",220f,144f);p.color=Color.rgb(93,200,242);p.strokeWidth=55f;c.drawLine(190f,510f,835f,510f,p);val path=Path();path.moveTo(720f,390f);path.lineTo(870f,510f);path.lineTo(720f,630f);path.close();c.drawPath(path,p);text("DRAG ACROSS",805f,83f);text("FINISH ON THIS LABEL",917f,48f)}
            "buddy"->{c.drawColor(green);text("PPE",650f,350f)}
            "ventilation"->{c.drawColor(navy);text("VENTILATE",190f,95f);p.color=Color.WHITE;c.drawCircle(512f,530f,65f,p);for(i in 0..3){c.save();c.rotate(i*90f,512f,530f);c.drawOval(480f,260f,635f,480f,p);c.restore()};text("CHECK AIRFLOW",925f,72f)}
            else->{c.drawColor(Color.rgb(250,191,32));val path=Path();path.moveTo(512f,170f);path.lineTo(905f,770f);path.lineTo(119f,770f);path.close();p.color=navy;c.drawPath(path,p);text("!",674f,380f);text("HAZARD AREA",920f,91f,navy)}
        }
        return bmp
    }
    private fun boxMesh():Mesh {
        val v=mutableListOf<Float>();val n=mutableListOf<Float>()
        val points=arrayOf(floatArrayOf(-.5f,-.5f,-.5f),floatArrayOf(.5f,-.5f,-.5f),floatArrayOf(.5f,.5f,-.5f),floatArrayOf(-.5f,.5f,-.5f),floatArrayOf(-.5f,-.5f,.5f),floatArrayOf(.5f,-.5f,.5f),floatArrayOf(.5f,.5f,.5f),floatArrayOf(-.5f,.5f,.5f))
        for(face in arrayOf(intArrayOf(4,5,6,7),intArrayOf(1,0,3,2),intArrayOf(0,4,7,3),intArrayOf(5,1,2,6),intArrayOf(3,7,6,2),intArrayOf(0,1,5,4))){val a=points[face[0]];val b=points[face[1]];val c=points[face[2]];val u=FloatArray(3){b[it]-a[it]};val w=FloatArray(3){c[it]-a[it]};val norm=listOf(u[1]*w[2]-u[2]*w[1],u[2]*w[0]-u[0]*w[2],u[0]*w[1]-u[1]*w[0]);for(i in intArrayOf(0,1,2,0,2,3)){v.addAll(points[face[i]].toList());n.addAll(norm)}}
        return Mesh(TrainingRenderer.floats(v.toFloatArray()),TrainingRenderer.floats(n.toFloatArray()))
    }
    private fun lathe(profile:List<Pair<Float,Float>>):Mesh {
        val v=mutableListOf<Float>();val n=mutableListOf<Float>()
        fun vertex(y:Float,r:Float,a:Double,slope:Float){v.addAll(listOf(r*cos(a).toFloat(),y,r*sin(a).toFloat()));n.addAll(listOf(cos(a).toFloat(),slope,sin(a).toFloat()))}
        for(j in 0 until profile.lastIndex){val (y,r)=profile[j];val (y2,r2)=profile[j+1];val slope=(r-r2)/(y2-y);for(i in 0 until 64){val a=i*2*PI/64;val b=(i+1)*2*PI/64;vertex(y,r,a,slope);vertex(y2,r2,a,slope);vertex(y2,r2,b,slope);vertex(y,r,a,slope);vertex(y2,r2,b,slope);vertex(y,r,b,slope)}}
        for((y,r)in listOf(profile.first(),profile.last()))for(i in 0 until 64){val sign=if(y==profile.first().first)-1f else 1f;for(a in listOf<Double?>(null,i*2*PI/64,(i+1)*2*PI/64)){v.addAll(if(a==null)listOf(0f,y,0f)else listOf(r*cos(a).toFloat(),y,r*sin(a).toFloat()));n.addAll(listOf(0f,sign,0f))}}
        return Mesh(TrainingRenderer.floats(v.toFloatArray()),TrainingRenderer.floats(n.toFloatArray()))
    }
    private fun torus():Mesh {
        val v=mutableListOf<Float>();val n=mutableListOf<Float>()
        for(i in 0 until 48)for(j in 0 until 12)for((di,dj)in listOf(0 to 0,1 to 0,1 to 1,0 to 0,1 to 1,0 to 1)){val a=(i+di)*2*PI/48;val b=(j+dj)*2*PI/12;v.addAll(listOf(((.8+.2*cos(b))*cos(a)).toFloat(),((.8+.2*cos(b))*sin(a)).toFloat(),(.2*sin(b)).toFloat()));n.addAll(listOf((cos(b)*cos(a)).toFloat(),(cos(b)*sin(a)).toFloat(),sin(b).toFloat()))}
        return Mesh(TrainingRenderer.floats(v.toFloatArray()),TrainingRenderer.floats(n.toFloatArray()))
    }
    private fun attribute(program:Int,name:String,data:FloatBuffer,size:Int){data.position(0);val at=glGetAttribLocation(program,name);glEnableVertexAttribArray(at);glVertexAttribPointer(at,size,GL_FLOAT,false,0,data)}
    private fun shaderProgram(v:String,f:String):Int {fun compile(type:Int,s:String):Int{val id=glCreateShader(type);glShaderSource(id,s);glCompileShader(id);val ok=IntArray(1);glGetShaderiv(id,GL_COMPILE_STATUS,ok,0);check(ok[0]!=0){glGetShaderInfoLog(id)};return id};val vs=compile(GL_VERTEX_SHADER,v);val fs=compile(GL_FRAGMENT_SHADER,f);val id=glCreateProgram();glAttachShader(id,vs);glAttachShader(id,fs);glLinkProgram(id);val ok=IntArray(1);glGetProgramiv(id,GL_LINK_STATUS,ok,0);check(ok[0]!=0){glGetProgramInfoLog(id)};glDeleteShader(vs);glDeleteShader(fs);return id}
}
