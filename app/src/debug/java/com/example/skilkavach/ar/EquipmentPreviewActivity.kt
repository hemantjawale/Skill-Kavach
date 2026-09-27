package com.example.skilkavach.ar

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import android.os.Bundle
import android.opengl.GLSurfaceView
import android.opengl.GLES20.*
import android.opengl.Matrix
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** Debug-only visual QA surface: exercises the same meshes, textures and shaders as AR. */
class EquipmentPreviewActivity : ComponentActivity() {
    private lateinit var surface: GLSurfaceView
    override fun onCreate(state:Bundle?) {
        super.onCreate(state)
        if (intent.getBooleanExtra("tutorial",false)) {
            setContent { MaterialTheme(colorScheme=lightColorScheme()) { ArInteractionDemo { finish() } } }
            return
        }
        val equipment=EquipmentRenderer()
        val fire=SmokeParticleRenderer()
        val effects=intent.getBooleanExtra("effects",false)
        val started=android.os.SystemClock.elapsedRealtime()
        surface=GLSurfaceView(this).apply {
            setEGLContextClientVersion(2)
            setRenderer(object:GLSurfaceView.Renderer {
                var width=1;var height=1
                override fun onSurfaceCreated(gl:GL10?,config:EGLConfig?){equipment.initialize();fire.initialize();glEnable(GL_DEPTH_TEST)}
                override fun onSurfaceChanged(gl:GL10?,w:Int,h:Int){width=w;height=h}
                override fun onDrawFrame(gl:GL10?){
                    glViewport(0,0,width,height);glClearColor(.89f,.92f,.96f,1f);glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
                    if(effects) {
                        val proj=FloatArray(16);Matrix.perspectiveM(proj,0,45f,width.toFloat()/height,.01f,10f)
                        val view=FloatArray(16);Matrix.setLookAtM(view,0,0f,.65f,2.1f,0f,.45f,0f,0f,1f,0f)
                        val mvp=FloatArray(16);Matrix.multiplyMM(mvp,0,proj,0,view,0)
                        val model=mvp.copyOf();Matrix.translateM(model,0,-.27f,0f,0f)
                        val t=(((android.os.SystemClock.elapsedRealtime()-started)%4000)/1400f).coerceIn(0f,1f)
                        equipment.draw("pin",model,t*t*(3f-2f*t))
                        fire.update(.23f,0f,0f);fire.draw(view,proj)
                        return
                    }
                    val ids=listOf("extinguisher","exit","alarm","stop","detector","ppe","permit","pin","handle")
                    val cw=width/3;val ch=height/3
                    for((i,id) in ids.withIndex()){
                        glViewport((i%3)*cw,(2-i/3)*ch,cw,ch)
                        val proj=FloatArray(16);Matrix.perspectiveM(proj,0,38f,cw.toFloat()/ch,.01f,10f)
                        val view=FloatArray(16);Matrix.setLookAtM(view,0,.18f,.40f,1.05f,0f,.23f,0f,0f,1f,0f)
                        val mvp=FloatArray(16);Matrix.multiplyMM(mvp,0,proj,0,view,0);equipment.draw(id,mvp)
                    }
                }
            })
        }
        setContentView(surface)
    }
    override fun onPause(){if (::surface.isInitialized) surface.onPause();super.onPause()}
    override fun onResume(){super.onResume();if (::surface.isInitialized) surface.onResume()}
}
