"use strict";

(() => {
  const CFG = window.CLAMON_CONFIG || {};
  const API = CFG.apiBase || "/api";
  const ESPERA_MS = 15000;
  const ESPERA_ANALISIS_MS = 45000;
  const ROLES = ["analista", "supervisor", "coordinador", "administrador", "responsable"];

  const K = { token: "clamon.token", expira: "clamon.expira" };

  const $ = (id) => document.getElementById(id);


  function token() {
    const t = sessionStorage.getItem(K.token);
    const expira = Number(sessionStorage.getItem(K.expira) || 0);
    if (t && Date.now() < expira) return t;
    if (t) borrarSesion();
    return null;
  }

  function borrarSesion() {
    sessionStorage.removeItem(K.token);
    sessionStorage.removeItem(K.expira);
  }

  async function iniciarSesion(evento) {
    evento.preventDefault();
    const boton = evento.submitter;
    if (boton) boton.disabled = true;
    try {
      const s = await api("POST", "/sesion", {
        usuario: $("entrar-usuario").value.trim(),
        contrasena: $("entrar-contrasena").value,
      });
      sessionStorage.setItem(K.token, s.token);
      sessionStorage.setItem(K.expira, String(Date.now() + s.expira_en * 1000));
      $("entrar-contrasena").value = "";
      avisar("");
      await cargarSesion();
    } catch (e) {
      avisar(e.estado === 401 ? "Usuario o contraseña incorrectos." : `Inicio de sesión: ${e.message}`);
    } finally {
      if (boton) boton.disabled = false;
    }
  }

  function cerrarSesion() {
    borrarSesion();
    pintarSesion(null);
    avisar("Sesión cerrada.", "ok");
  }


  class ErrorApi extends Error {
    constructor(estado, cuerpo) {
      super(cuerpo && cuerpo.detalle ? `${cuerpo.error}: ${cuerpo.detalle}` : `HTTP ${estado}`);
      this.estado = estado;
    }
  }

  async function api(metodo, ruta, cuerpo, espera = ESPERA_MS) {
    const encabezados = { Accept: "application/json" };
    const t = token();
    if (t && ruta !== "/sesion") encabezados.Authorization = `Bearer ${t}`;
    if (cuerpo !== undefined) encabezados["Content-Type"] = "application/json";
    const r = await fetch(`${API}${ruta}`, {
      method: metodo,
      headers: encabezados,
      body: cuerpo !== undefined ? JSON.stringify(cuerpo) : undefined,
      signal: AbortSignal.timeout(espera),
    });
    const datos = await r.json().catch(() => null);
    if (r.status === 401 && ruta !== "/sesion") {
      borrarSesion();
      pintarSesion(null);
      avisar("La sesión expiró o no es válida. Inicia sesión de nuevo.");
    }
    if (!r.ok) throw new ErrorApi(r.status, datos);
    return datos;
  }


  function el(etiqueta, texto, clase) {
    const e = document.createElement(etiqueta);
    if (texto !== undefined && texto !== null) e.textContent = String(texto);
    if (clase) e.className = clase;
    return e;
  }

  function avisar(texto, tipo = "error") {
    const a = $("aviso");
    a.textContent = texto;
    a.className = `aviso ${tipo}`;
    a.hidden = !texto;
  }

  function vaciar(nodo) {
    nodo.replaceChildren();
    return nodo;
  }

  function mostrarError(destino, e) {
    vaciar(destino).append(el("p", e.message || String(e), "error"));
  }

  function fecha(valor) {
    return valor ? new Date(valor).toLocaleString() : "—";
  }

  function casillasDeRoles(fieldset, prefijo) {
    for (const rol of ROLES) {
      const etiqueta = el("label", null, "casilla");
      const casilla = document.createElement("input");
      casilla.type = "checkbox";
      casilla.value = rol;
      casilla.id = `${prefijo}-${rol}`;
      etiqueta.append(casilla, ` ${rol}`);
      fieldset.append(etiqueta);
    }
  }

  function rolesMarcados(fieldset) {
    return [...fieldset.querySelectorAll("input:checked")].map((c) => c.value);
  }

  function tablaHallazgos(resultado) {
    const cont = document.createElement("div");
    const resumen = el("p");
    resumen.append("Nivel ", el("span", resultado.nivel, `insignia nivel-${resultado.nivel}`),
      ` · ${resultado.total ?? resultado.total_hallazgos} hallazgos`);
    cont.append(resumen);
    if (resultado.hallazgos && resultado.hallazgos.length) {
      const t = el("table");
      const cab = t.createTHead().insertRow();
      for (const h of ["Tipo", "Valor enmascarado", "Posición"]) cab.append(el("th", h));
      const cuerpo = t.createTBody();
      for (const h of resultado.hallazgos) {
        const fila = cuerpo.insertRow();
        fila.append(el("td", h.tipo), el("td", h.valor, "mono"), el("td", `${h.inicio}–${h.fin}`));
      }
      cont.append(t);
    }
    return cont;
  }

  function pintarAnalisis(a) {
    const d = vaciar($("resultado-analisis"));
    d.append(el("p", `Análisis ${a.id} del sitio ${a.sitio_id}: ${a.estado}`));
    if (a.objeto) d.append(el("p", `Objeto: ${a.objeto} (${a.bytes} bytes)`, "mono"));
    d.append(tablaHallazgos(a));
  }

  function pintarSesion(yo) {
    const roles = yo ? yo.roles : [];
    const tiene = (r) => roles.includes(r);
    $("sec-entrar").hidden = Boolean(yo);
    $("btn-salir").hidden = !yo;
    $("usuario").hidden = !yo;
    $("usuario").textContent = yo ? yo.usuario : "";
    $("roles").hidden = !yo;
    $("roles").textContent = yo ? `Roles: ${roles.length ? roles.join(", ") : "ninguno"}` : "";
    $("sec-sitios").hidden = !(tiene("analista") || tiene("supervisor"));
    $("form-sitio").hidden = !tiene("supervisor");
    $("sec-analisis").hidden = !(tiene("analista") || tiene("supervisor"));
    $("form-analisis").hidden = !tiene("analista");
    $("sec-clasificar").hidden = !tiene("analista");
    $("sec-diagnostico").hidden = !tiene("administrador");
    $("sec-usuarios").hidden = !tiene("administrador");
  }


  async function cargarSalud() {
    const insignia = $("estado-backend");
    try {
      const s = await api("GET", "/salud");
      insignia.textContent = `${s.estado} · ${s.servicio} ${s.version || ""}`.trim();
      insignia.className = "insignia ok";
    } catch (e) {
      insignia.textContent = `sin respuesta (${e.message})`;
      insignia.className = "insignia mal";
    }
  }

  async function cargarSesion() {
    if (!token()) {
      pintarSesion(null);
      return;
    }
    try {
      const yo = await api("GET", "/yo");
      pintarSesion(yo);
      if (yo.roles.includes("analista") || yo.roles.includes("supervisor")) cargarSitios();
      if (yo.roles.includes("administrador")) cargarUsuarios();
    } catch (e) {
      if (e.estado !== 401) avisar(`No se pudo consultar /api/yo: ${e.message}`);
    }
  }

  async function cargarSitios() {
    try {
      const sitios = await api("GET", "/sitios");
      const tabla = $("tabla-sitios");
      const cuerpo = vaciar(tabla.tBodies[0]);
      const lista = vaciar($("sitio-analisis"));
      for (const s of sitios) {
        const fila = cuerpo.insertRow();
        fila.append(el("td", s.id), el("td", s.url, "mono"), el("td", s.creado_por), el("td", fecha(s.creado_en)));
        const opcion = el("option", `${s.id} · ${s.host}`);
        opcion.value = s.id;
        lista.append(opcion);
      }
      tabla.hidden = sitios.length === 0;
    } catch (e) {
      avisar(`Sitios: ${e.message}`);
    }
  }

  async function registrarSitio(evento) {
    evento.preventDefault();
    try {
      const s = await api("POST", "/sitios", { url: $("url-sitio").value.trim() });
      avisar(`Sitio ${s.id} registrado: ${s.host}`, "ok");
      $("url-sitio").value = "";
      await cargarSitios();
    } catch (e) {
      avisar(`Registro: ${e.message}`);
    }
  }

  async function lanzarAnalisis(evento) {
    evento.preventDefault();
    const destino = vaciar($("resultado-analisis"));
    destino.append(el("p", "Analizando… la primera extracción puede tardar."));
    const boton = evento.submitter;
    if (boton) boton.disabled = true;
    try {
      pintarAnalisis(await api("POST", "/analisis", { sitio_id: Number($("sitio-analisis").value) },
        ESPERA_ANALISIS_MS));
    } catch (e) {
      mostrarError(destino, e);
    } finally {
      if (boton) boton.disabled = false;
    }
  }

  async function consultarAnalisis(evento) {
    evento.preventDefault();
    try {
      pintarAnalisis(await api("GET", `/analisis/${encodeURIComponent($("id-analisis").value)}`));
    } catch (e) {
      mostrarError($("resultado-analisis"), e);
    }
  }

  async function clasificar(evento) {
    evento.preventDefault();
    const destino = $("resultado-clasificar");
    try {
      vaciar(destino).append(tablaHallazgos(await api("POST", "/clasificacion", { texto: $("texto").value })));
    } catch (e) {
      mostrarError(destino, e);
    }
  }

  async function diagnostico() {
    const destino = vaciar($("resultado-diagnostico"));
    destino.append(el("p", "Consultando…"));
    try {
      const d = await api("GET", "/diagnostico");
      vaciar(destino).append(el("p", `Estado general: ${d.estado_general}`,
        d.estado_general === "ok" ? "ok" : "error"));
      const t = el("table");
      const cab = t.createTHead().insertRow();
      for (const h of ["Dependencia", "Estado", "Latencia", "Detalle"]) cab.append(el("th", h));
      const cuerpo = t.createTBody();
      for (const [nombre, dep] of Object.entries(d.dependencias)) {
        const fila = cuerpo.insertRow();
        fila.append(el("td", nombre), el("td", dep.estado, `insignia ${dep.estado === "ok" ? "ok" : "mal"}`),
          el("td", `${dep.latencia_ms} ms`), el("td", dep.detalle, "mono"));
      }
      destino.append(t);
    } catch (e) {
      mostrarError(destino, e);
    }
  }

  async function cargarUsuarios() {
    try {
      const cuentas = await api("GET", "/usuarios");
      const cuerpo = vaciar($("tabla-usuarios").tBodies[0]);
      const lista = vaciar($("mod-usuario"));
      for (const c of cuentas) {
        const estado = !c.activo ? "inactivo" : c.bloqueado ? "bloqueado" : "activo";
        const fila = cuerpo.insertRow();
        fila.append(el("td", c.usuario, "mono"), el("td", c.roles.join(", ") || "—"),
          el("td", estado, `insignia ${estado === "activo" ? "ok" : "mal"}`), el("td", fecha(c.ultimo_acceso)));
        const opcion = el("option", c.usuario);
        opcion.value = c.usuario;
        lista.append(opcion);
      }
    } catch (e) {
      avisar(`Usuarios: ${e.message}`);
    }
  }

  async function crearUsuario(evento) {
    evento.preventDefault();
    try {
      const c = await api("POST", "/usuarios", {
        usuario: $("nuevo-usuario").value.trim(),
        contrasena: $("nuevo-contrasena").value,
        roles: rolesMarcados($("nuevo-roles")),
      });
      avisar(`Usuario ${c.usuario} creado.`, "ok");
      evento.target.reset();
      await cargarUsuarios();
    } catch (e) {
      avisar(`Crear usuario: ${e.message}`);
    }
  }

  async function modificarUsuario(evento) {
    evento.preventDefault();
    const cambios = {};
    if ($("mod-contrasena").value) cambios.contrasena = $("mod-contrasena").value;
    if ($("mod-estado").value) cambios.activo = $("mod-estado").value === "true";
    if ($("mod-cambiar-roles").checked) cambios.roles = rolesMarcados($("mod-roles"));
    const usuario = $("mod-usuario").value;
    try {
      await api("POST", `/usuarios/${encodeURIComponent(usuario)}`, cambios);
      avisar(`Usuario ${usuario} actualizado.`, "ok");
      evento.target.reset();
      $("mod-roles").disabled = true;
      await cargarUsuarios();
    } catch (e) {
      avisar(`Modificar usuario: ${e.message}`);
    }
  }


  function iniciar() {
    casillasDeRoles($("nuevo-roles"), "nuevo");
    casillasDeRoles($("mod-roles"), "mod");
    $("form-entrar").addEventListener("submit", iniciarSesion);
    $("btn-salir").addEventListener("click", cerrarSesion);
    $("btn-sitios").addEventListener("click", cargarSitios);
    $("form-sitio").addEventListener("submit", registrarSitio);
    $("form-analisis").addEventListener("submit", lanzarAnalisis);
    $("form-consulta").addEventListener("submit", consultarAnalisis);
    $("form-clasificar").addEventListener("submit", clasificar);
    $("btn-diagnostico").addEventListener("click", diagnostico);
    $("form-crear-usuario").addEventListener("submit", crearUsuario);
    $("form-modificar-usuario").addEventListener("submit", modificarUsuario);
    $("mod-cambiar-roles").addEventListener("change", (e) => { $("mod-roles").disabled = !e.target.checked; });

    cargarSalud();
    cargarSesion();
  }

  iniciar();
})();
