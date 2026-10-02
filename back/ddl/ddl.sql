
CREATE OR REPLACE FUNCTION public.atualiza_dt_atz()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
BEGIN
    NEW.dt_atz = CURRENT_TIMESTAMP;
    
    RETURN NEW;
END;
$function$
;


CREATE TABLE public.usuarios (
	id bigserial NOT NULL,
	senha varchar(255) NOT NULL,
	tipo_perfil varchar(255) NOT NULL,
	usuario varchar(255) NOT NULL,
	dt_atz timestamp NULL,
	CONSTRAINT uk_3m5n1w5trapxlbo2s42ugwdmd UNIQUE (usuario),
	CONSTRAINT usuarios_pkey PRIMARY KEY (id),
	CONSTRAINT usuarios_tipo_perfil_check CHECK (((tipo_perfil)::text = ANY (ARRAY[('JOGADOR'::character varying)::text, ('OBSERVADOR'::character varying)::text, ('ADMIN'::character varying)::text, ('SUPER'::character varying)::text, ('TESTE'::character varying)::text])))
);

-- Table Triggers

CREATE TRIGGER trg_atualiza_dt_atz BEFORE
INSERT
    OR
UPDATE
    ON
    public.usuarios FOR EACH ROW EXECUTE FUNCTION atualiza_dt_atz();
	
	
CREATE TABLE public.salas (
	id bigserial NOT NULL,
	ativa bool NOT NULL,
	codigo varchar(255) NOT NULL,
	criada_em timestamptz(6) NOT NULL,
	nome varchar(255) NOT NULL,
	id_moderador int8 NOT NULL,
	CONSTRAINT salas_pkey PRIMARY KEY (id),
	CONSTRAINT uk_ejycac9sutc609succl71w34k UNIQUE (codigo),
	CONSTRAINT fk2c8pgtblpou4xwtl6kjvgtc8g FOREIGN KEY (id_moderador) REFERENCES public.usuarios(id)
);
	
CREATE TABLE public.tarefas (
	id bigserial NOT NULL,
	descricao varchar(255) NULL,
	estimada bool NOT NULL,
	horas_reveladas bool NOT NULL,
	liberada bool NOT NULL,
	numero int8 NULL,
	pontos_revelados bool NOT NULL,
	prioridade int4 NULL,
	status varchar(255) NULL,
	titulo varchar(255) NULL,
	sprint varchar(255) NULL,
	horas_liberadas bool DEFAULT false NOT NULL,
	liberada_em timestamptz(6) NULL,
	dt_atz timestamp NULL,
	rodada_atual int4 NULL,
	estimada_em timestamptz(6) NULL,
	horas_liberadas_em timestamptz(6) NULL,
	id_sala int8 NULL,
	dados_extras text NULL,
	pulada bool DEFAULT false NULL,
	horas_teste_liberadas_em timestamptz(6) NULL,
	horas_teste_liberadas bool DEFAULT false NULL,
	horas_teste_reveladas bool DEFAULT false NULL,
	CONSTRAINT tarefas_pkey PRIMARY KEY (id),
	CONSTRAINT fk6b3rmgx9n9fseil9qxiqgigd8 FOREIGN KEY (id_sala) REFERENCES public.salas(id)
);

-- Table Triggers

CREATE TRIGGER trg_atualiza_dt_atz BEFORE
INSERT
    OR
UPDATE
    ON
    public.tarefas FOR EACH ROW EXECUTE FUNCTION atualiza_dt_atz();



CREATE TABLE public.estimativas (
	id bigserial NOT NULL,
	horas float8 NULL,
	horas_reveladas bool NULL,
	pontos int4 NULL,
	revelada bool NULL,
	id_tarefas int8 NULL,
	dt_atz timestamp NULL,
	id_usuario int8 NULL,
	rodada int4 NULL,
	votado_em_horas timestamptz(6) NULL,
	votado_em_pontos timestamptz(6) NULL,
	horas_teste float8 NULL,
	horas_teste_reveladas bool NULL,
	CONSTRAINT estimativas_pkey PRIMARY KEY (id),
	CONSTRAINT fk_estimativas_usuarios FOREIGN KEY (id_usuario) REFERENCES public.usuarios(id),
	CONSTRAINT fks9yt2p3lff94bv2ap08ewfxt5 FOREIGN KEY (id_tarefas) REFERENCES public.tarefas(id)
);

-- Table Triggers

CREATE TRIGGER trg_atualiza_dt_atz BEFORE
INSERT
    OR
UPDATE
    ON
    public.estimativas FOR EACH ROW EXECUTE FUNCTION atualiza_dt_atz();
	

CREATE TABLE public.sala_membros (
	id bigserial NOT NULL,
	id_sala int8 NOT NULL,
	id_usuario int8 NOT NULL,
	CONSTRAINT sala_membros_pkey PRIMARY KEY (id),
	CONSTRAINT ukfm1cf34it8rnop08ffhcd1rif UNIQUE (id_sala, id_usuario),
	CONSTRAINT fk7l1agd7e1424x3neelss4kwmw FOREIGN KEY (id_sala) REFERENCES public.salas(id),
	CONSTRAINT fkftre4n8axpjiigytbf80kyxbq FOREIGN KEY (id_usuario) REFERENCES public.usuarios(id)
);

CREATE TABLE public.task_participants (
	id bigserial NOT NULL,
	task_id int8 NOT NULL,
	dt_atz timestamp NULL,
	id_usuario int8 NULL,
	CONSTRAINT task_participants_pkey PRIMARY KEY (id),
	CONSTRAINT ukf3oyvrid8h7f6sbn7ybg90am8 UNIQUE (task_id, id_usuario),
	CONSTRAINT fk_task_participants_usuarios FOREIGN KEY (id_usuario) REFERENCES public.usuarios(id),
	CONSTRAINT task_participants_tarefas_fk FOREIGN KEY (task_id) REFERENCES public.tarefas(id) ON DELETE CASCADE
);

-- Table Triggers

CREATE TRIGGER trg_atualiza_dt_atz BEFORE
INSERT
    OR
UPDATE
    ON
    public.task_participants FOR EACH ROW EXECUTE FUNCTION atualiza_dt_atz();

CREATE TABLE public.user_sprints (
	id bigserial NOT NULL,
	sprint varchar(255) NULL,
	dt_atz timestamp NULL,
	id_usuario int8 NULL,
	CONSTRAINT ukf5g6s86qok9dbjpduvgj2q6tu UNIQUE (id_usuario, sprint),
	CONSTRAINT user_sprints_pkey PRIMARY KEY (id),
	CONSTRAINT fk_user_sprints_usuarios FOREIGN KEY (id_usuario) REFERENCES public.usuarios(id)
);

-- Table Triggers

CREATE TRIGGER trg_atualiza_dt_atz BEFORE
INSERT
    OR
UPDATE
    ON
    public.user_sprints FOR EACH ROW EXECUTE FUNCTION atualiza_dt_atz();	
	
CREATE SEQUENCE public.estimativas_id_seq
	INCREMENT BY 1
	MINVALUE 1
	MAXVALUE 9223372036854775807
	START 1
	CACHE 1
	NO CYCLE;

CREATE SEQUENCE public.sala_membros_id_seq
	INCREMENT BY 1
	MINVALUE 1
	MAXVALUE 9223372036854775807
	START 1
	CACHE 1
	NO CYCLE;

CREATE SEQUENCE public.salas_id_seq
	INCREMENT BY 1
	MINVALUE 1
	MAXVALUE 9223372036854775807
	START 1
	CACHE 1
	NO CYCLE;

CREATE SEQUENCE public.tarefas_id_seq
	INCREMENT BY 1
	MINVALUE 1
	MAXVALUE 9223372036854775807
	START 1
	CACHE 1
	NO CYCLE;

CREATE SEQUENCE public.task_participants_id_seq
	INCREMENT BY 1
	MINVALUE 1
	MAXVALUE 9223372036854775807
	START 1
	CACHE 1
	NO CYCLE;

CREATE SEQUENCE public.user_sprints_id_seq
	INCREMENT BY 1
	MINVALUE 1
	MAXVALUE 9223372036854775807
	START 1
	CACHE 1
	NO CYCLE;	
	
CREATE SEQUENCE public.usuarios_id_seq
	INCREMENT BY 1
	MINVALUE 1
	MAXVALUE 9223372036854775807
	START 1
	CACHE 1
	NO CYCLE;	