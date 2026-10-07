/* Live shader from the owner's metallic_baby_pink_suite, in logo-matched pink. */
(() => {
  const canvas = document.createElement('canvas');
  canvas.className = 'liquid-background';
  canvas.setAttribute('aria-hidden', 'true');
  document.body.prepend(canvas);
  const reduced = matchMedia('(prefers-reduced-motion: reduce)');
  const gl = canvas.getContext('webgl', {alpha: false, antialias: false, powerPreference: 'low-power'});
  if (!gl) return;
  const vertex = 'attribute vec2 position; void main(){gl_Position=vec4(position,0.,1.);}';
  const fragment = `precision mediump float;
    uniform vec2 u_resolution;
    uniform float u_time;
    void main(){
      vec2 st=gl_FragCoord.xy/u_resolution;
      vec2 uv=(gl_FragCoord.xy*2.-u_resolution)/min(u_resolution.x,u_resolution.y);
      float t=u_time*.45;
      float w1=sin(uv.x*2.2+t*.8+cos(uv.y*1.8+t*.5));
      float w2=cos(uv.y*2.5-t*.7+sin(uv.x*1.5-t*.4));
      float wave=w1*.6+w2*.4;
      float fold=sin(uv.x*2.6+uv.y*2.+wave*2.2);
      float ridge=pow(abs(cos(fold*1.4)),4.);
      vec3 deepPink=vec3(.40,.045,.16);
      vec3 babyPink=vec3(.90,.30,.51);
      vec3 roseChrome=vec3(1.,.75,.86);
      vec3 chrome=mix(deepPink,babyPink,smoothstep(-.8,.8,wave));
      chrome=mix(chrome,roseChrome,clamp(ridge*.8+pow(ridge,3.)*.6,0.,1.));
      // Narrow moving crests retain the polished liquid-metal contrast.
      chrome=mix(chrome,vec3(.22,.018,.08),pow(1.-ridge,5.)*.42);
      // Preserve the logo's deeper top and luminous rose-pink lower folds.
      chrome*=mix(1.04,.78,smoothstep(.15,1.,st.y));
      gl_FragColor=vec4(chrome,1.);
    }`;
  function compile(type, source) {
    const shader = gl.createShader(type);
    gl.shaderSource(shader, source); gl.compileShader(shader);
    if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) { gl.deleteShader(shader); throw new Error('Shader unavailable'); }
    return shader;
  }
  let program;
  try {
    program=gl.createProgram();
    gl.attachShader(program,compile(gl.VERTEX_SHADER,vertex));
    gl.attachShader(program,compile(gl.FRAGMENT_SHADER,fragment));
    gl.linkProgram(program);
    if (!gl.getProgramParameter(program,gl.LINK_STATUS)) return;
  } catch { return; }
  gl.useProgram(program);
  const buffer=gl.createBuffer(); gl.bindBuffer(gl.ARRAY_BUFFER,buffer);
  gl.bufferData(gl.ARRAY_BUFFER,new Float32Array([-1,-1,1,-1,-1,1,-1,1,1,-1,1,1]),gl.STATIC_DRAW);
  const position=gl.getAttribLocation(program,'position');
  gl.enableVertexAttribArray(position); gl.vertexAttribPointer(position,2,gl.FLOAT,false,0,0);
  const resolution=gl.getUniformLocation(program,'u_resolution');
  const time=gl.getUniformLocation(program,'u_time');
  let frame=0, last=0, elapsed=0, lost=false;
  function draw(now) {
    frame=0;
    if (lost || document.hidden) return;
    if (!last || now-last>=1000/30) {
      if (last) elapsed+=Math.min(now-last,100);
      last=now;
      gl.uniform1f(time,reduced.matches ? 0 : elapsed/1000);
      gl.drawArrays(gl.TRIANGLES,0,6);
    }
    if (!reduced.matches) frame=requestAnimationFrame(draw);
  }
  function resume() {
    cancelAnimationFrame(frame); last=0;
    if (!lost && !document.hidden) frame=requestAnimationFrame(draw);
  }
  function resize() {
    const scale=Math.min(devicePixelRatio||1,1.25,1400/innerWidth);
    canvas.width=Math.max(1,Math.round(innerWidth*scale)); canvas.height=Math.max(1,Math.round(innerHeight*scale));
    gl.viewport(0,0,canvas.width,canvas.height); gl.uniform2f(resolution,canvas.width,canvas.height);
    resume();
  }
  // Live mode starts immediately; the logo texture is only the CSS fallback.
  resize();
  canvas.classList.add('ready');
  addEventListener('resize',resize,{passive:true});
  document.addEventListener('visibilitychange',resume);
  reduced.addEventListener('change',resume);
  canvas.addEventListener('webglcontextlost',event=>{event.preventDefault();lost=true;cancelAnimationFrame(frame);canvas.classList.remove('ready');});
})();
