//! VERTEX
attribute vec3 aVertexPosition;

uniform mat4 uModelViewProj;

void main(void) {
  gl_Position = uModelViewProj * vec4(aVertexPosition, 1.0);
}

//! FRAGMENT
uniform vec3 uDiffuseColor;

void main(void) {
  gl_FragColor = vec4(uDiffuseColor, 1);
}
