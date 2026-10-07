/* Animate the owner's actual liquid artwork; every route shares this layer. */
(() => {
  const fallback = document.createElement('div');
  fallback.className = 'liquid-art-fallback';
  fallback.setAttribute('aria-hidden', 'true');
  const canvas = document.createElement('canvas');
  canvas.className = 'liquid-background';
  canvas.setAttribute('aria-hidden', 'true');
  document.body.prepend(fallback, canvas);
  function visibility() {
    fallback.classList.toggle('paused', document.hidden);
  }
  document.addEventListener('visibilitychange', visibility);
  visibility();
  const reduced = matchMedia('(prefers-reduced-motion: reduce)');
  const gl = canvas.getContext('webgl', {alpha: false, antialias: false, powerPreference: 'low-power'});
  if (!gl) return;
  const vertex = 'attribute vec2 position; void main(){gl_Position=vec4(position,0.,1.);}';
  const fragment = `precision mediump float;
    uniform vec2 u_resolution;
    uniform float u_time;
    uniform sampler2D u_art;
    uniform vec2 u_artSize;
    void main(){
      vec2 st=gl_FragCoord.xy/u_resolution;
      float t=u_time*.60;
      float screenAspect=u_resolution.x/u_resolution.y;
      float artAspect=u_artSize.x/u_artSize.y;
      vec2 cover=vec2(min(1.,screenAspect/artAspect),min(1.,artAspect/screenAspect));
      // Show the full abstract artwork on portrait screens instead of a flat crop.
      cover=mix(vec2(1.),cover,smoothstep(1.,1.6,screenAspect));
      float breathing=.82+sin(t*.55)*.018;
      vec2 uv=(vec2(st.x,1.-st.y)-.5)*cover*breathing+.5;
      // Flow and breathing deform the supplied folds rather than replace them.
      // Taper displacement at the edges so the artwork never stretches there.
      vec2 flowRoom=vec2(1.)-abs(st-.5)*1.1;
      uv+=vec2(sin(st.y*4.+t)+sin(st.x*3.-t*.6),cos(st.x*4.-t*.8))*cover*flowRoom*.038;
      uv+=vec2(sin(t*.65),cos(t*.55))*cover*flowRoom*.050;
      vec3 art=texture2D(u_art,clamp(uv,0.,1.)).rgb;
      vec3 shadowPink=vec3(.75,.40,.56);
      // Preserve the reference's continuous gloss and fine highlight detail.
      vec3 chrome=max(art*vec3(.97,.83,.88),shadowPink);
      // Rosier at the top, baby-pink reflections below; no opaque page scrim.
      chrome=mix(chrome,shadowPink,st.y*.12);
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
  const texture=gl.createTexture();
  gl.bindTexture(gl.TEXTURE_2D,texture);
  gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.LINEAR);
  gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.LINEAR);
  gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.CLAMP_TO_EDGE);
  gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE);
  gl.uniform1i(gl.getUniformLocation(program,'u_art'),0);
  let frame=0, last=0, elapsed=0, lost=false, ready=false;
  function draw(now) {
    frame=0;
    if (!ready || lost || document.hidden) return;
    if (!last || now-last>=1000/30) {
      if (last) elapsed+=Math.min(now-last,100);
      last=now;
      gl.uniform1f(time,reduced.matches ? 0 : elapsed/1000);
      gl.drawArrays(gl.TRIANGLES,0,6);
      canvas.classList.add('ready');
      document.body.setAttribute('data-liquid-ready', '');
    }
    if (!reduced.matches) frame=requestAnimationFrame(draw);
  }
  function resume() {
    cancelAnimationFrame(frame); last=0;
    if (ready && !lost && !document.hidden) frame=requestAnimationFrame(draw);
  }
  function resize() {
    const scale=Math.min(devicePixelRatio||1,1.25,1400/innerWidth);
    canvas.width=Math.max(1,Math.round(innerWidth*scale)); canvas.height=Math.max(1,Math.round(innerHeight*scale));
    gl.viewport(0,0,canvas.width,canvas.height); gl.uniform2f(resolution,canvas.width,canvas.height);
    resume();
  }
  const artwork=new Image();
  artwork.onload=()=>{
    gl.bindTexture(gl.TEXTURE_2D,texture);
    gl.texImage2D(gl.TEXTURE_2D,0,gl.RGB,gl.RGB,gl.UNSIGNED_BYTE,artwork);
    gl.uniform2f(gl.getUniformLocation(program,'u_artSize'),artwork.width,artwork.height);
    ready=true; resize();
  };
  artwork.src='assets/liquid-pink/floating-reference.webp';
  addEventListener('resize',resize,{passive:true});
  document.addEventListener('visibilitychange',resume);
  reduced.addEventListener('change',resume);
  canvas.addEventListener('webglcontextlost',event=>{
    event.preventDefault(); lost=true; cancelAnimationFrame(frame);
    canvas.classList.remove('ready'); document.body.removeAttribute('data-liquid-ready');
  });
})();
