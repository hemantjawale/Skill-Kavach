package com.example.skilkavach.ar

import android.opengl.GLES20.*
import android.opengl.Matrix
import android.os.SystemClock
import kotlin.math.*

/** Soft, camera-facing procedural flame tongues and turbulent smoke, timed in seconds. */
class SmokeParticleRenderer(private val maxParticles: Int = 48) {
    private var program = 0
    private val quad = TrainingRenderer.floats(floatArrayOf(-1f,-1f,1f,-1f,-1f,1f,1f,1f))
    private val model = FloatArray(16)
    private val mvp = FloatArray(16)
    private val vp = FloatArray(16)
    private val start = SystemClock.elapsedRealtime()
    private var time = 0f
    private var x = 0f
    private var y = 0f
    private var z = 0f
    private var density = 1f
    private var position = 0
    private var matrix = 0
    private var age = 0
    private var seed = 0
    private var smoke = 0
    private var opacity = 0

    fun initialize() {
        fun shader(type: Int, source: String): Int {
            val id = glCreateShader(type)
            glShaderSource(id, source); glCompileShader(id)
            val ok = IntArray(1); glGetShaderiv(id, GL_COMPILE_STATUS, ok, 0)
            check(ok[0] != 0) { glGetShaderInfoLog(id) }
            return id
        }
        val vertex = shader(GL_VERTEX_SHADER, """
            attribute vec2 p; uniform mat4 mvp; varying vec2 uv;
            void main(){ uv=p; gl_Position=mvp*vec4(p,0.,1.); }
        """)
        val fragment = shader(GL_FRAGMENT_SHADER, """
            precision mediump float;
            varying vec2 uv;
            uniform float age, seed, smoke, opacity;
            float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
            float noise(vec2 p){
                vec2 i=floor(p),f=fract(p);f=f*f*(3.-2.*f);
                return mix(mix(hash(i),hash(i+vec2(1.,0.)),f.x),
                           mix(hash(i+vec2(0.,1.)),hash(i+vec2(1.,1.)),f.x),f.y);
            }
            void main(){
                float n=noise(uv*3.2+vec2(seed, -age*3.));
                float detail=noise(uv*7.+vec2(seed*2.,-age*5.));
                float fade=smoothstep(0.,.12,age)*(1.-smoothstep(.55,1.,age));
                float envelope=1.-smoothstep(.65,1.,length(uv));
                if(smoke>.5){
                    float a=envelope*(.45+.55*n)*fade*opacity*.19;
                    gl_FragColor=vec4(mix(vec3(.12,.13,.15),vec3(.42,.43,.45),detail),a);
                }else{
                    float height=uv.y*.5+.5;
                    float width=mix(.75,.08,height);
                    float bend=(n-.5)*.32*height;
                    float edge=1.-smoothstep(width*.25,width,abs(uv.x+bend));
                    float a=edge*envelope*fade*(.65+.35*detail)*opacity*.75;
                    float heat=clamp((1.-height)*.85+edge*.3,0.,1.);
                    vec3 color=mix(vec3(1.,.10,.008),vec3(1.,.62,.06),heat);
                    color=mix(color,vec3(1.,.94,.65),smoothstep(.85,1.,heat));
                    gl_FragColor=vec4(color,a);
                }
            }
        """)
        program = glCreateProgram()
        glAttachShader(program,vertex);glAttachShader(program,fragment);glLinkProgram(program)
        val ok=IntArray(1);glGetProgramiv(program,GL_LINK_STATUS,ok,0)
        check(ok[0]!=0){glGetProgramInfoLog(program)}
        glDeleteShader(vertex);glDeleteShader(fragment)
        position=glGetAttribLocation(program,"p");matrix=glGetUniformLocation(program,"mvp")
        age=glGetUniformLocation(program,"age");seed=glGetUniformLocation(program,"seed")
        smoke=glGetUniformLocation(program,"smoke");opacity=glGetUniformLocation(program,"opacity")
    }

    fun update(hazardX: Float, hazardY: Float, hazardZ: Float, density: Float = 1f) {
        x=hazardX;y=hazardY;z=hazardZ;this.density=density.coerceIn(0f,1f)
        time=((SystemClock.elapsedRealtime()-start)/1000.0 % 3600).toFloat()
    }

    fun draw(viewMatrix: FloatArray, projectionMatrix: FloatArray) {
        if(program==0)return
        Matrix.multiplyMM(vp,0,projectionMatrix,0,viewMatrix,0)
        glEnable(GL_BLEND);glDepthMask(false);glUseProgram(program)
        glEnableVertexAttribArray(position);quad.position(0)
        glVertexAttribPointer(position,2,GL_FLOAT,false,0,quad)
        glUniform1f(opacity,density)
        // Smoke first, then luminous flames. Neither writes opaque rectangular depth.
        for(pass in 0..1) {
            val isSmoke=pass==0
            glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA)
            glUniform1f(smoke,if(isSmoke)1f else 0f)
            for(i in 0 until maxParticles/2) {
                val phase=i*.618034f
                val life=if(isSmoke)3.6f else 1.35f
                val t=(time/life+phase)%1f
                val angle=i*2.39996f
                val radius=if(isSmoke).09f+t*.16f else .10f
                val px=x+cos(angle)*radius+sin(time*1.4f+i)*t*.035f
                val py=y+.1f+t*(if(isSmoke)1.0f else .40f)
                val pz=z+sin(angle)*radius
                Matrix.setIdentityM(model,0)
                model[0]=viewMatrix[0];model[1]=viewMatrix[4];model[2]=viewMatrix[8]
                model[4]=viewMatrix[1];model[5]=viewMatrix[5];model[6]=viewMatrix[9]
                model[8]=viewMatrix[2];model[9]=viewMatrix[6];model[10]=viewMatrix[10]
                model[12]=px;model[13]=py;model[14]=pz
                val size=if(isSmoke).15f+t*.26f else .09f*(1f-t*.5f)
                Matrix.scaleM(model,0,size,if(isSmoke)size else .20f,1f)
                Matrix.multiplyMM(mvp,0,vp,0,model,0)
                glUniformMatrix4fv(matrix,1,false,mvp,0)
                glUniform1f(age,t);glUniform1f(seed,i*7.13f)
                glDrawArrays(GL_TRIANGLE_STRIP,0,4)
            }
        }
        glDisableVertexAttribArray(position);glDepthMask(true)
        glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);glDisable(GL_BLEND)
    }
}

