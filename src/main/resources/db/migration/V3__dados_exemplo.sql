-- Dados de EXEMPLO para demonstração (Swagger, prints, vídeo). Todos fictícios.
-- Os CPFs têm dígitos verificadores válidos (passam na validação), mas não pertencem a ninguém de propósito.
-- ON CONFLICT DO NOTHING: se o banco já tiver esses registros, a migration não quebra.

INSERT INTO pacientes (nome, cpf, data_nascimento, telefone, email) VALUES
    ('Maria Fernanda Alves', '28476195346', '1988-03-14', '11987650001', 'maria.alves@exemplo.com'),
    ('João Pedro Nogueira',  '73520814617', '1975-11-02', '11987650002', 'joao.nogueira@exemplo.com'),
    ('Beatriz Carvalho',     '41859206794', '2001-07-21', '11987650003', 'beatriz.carvalho@exemplo.com'),
    ('Carlos Eduardo Lima',  '60293741840', '1992-01-30', '11987650004', NULL),
    ('Helena Martins',       '15734086207', '1966-09-09', '11987650005', 'helena.martins@exemplo.com')
ON CONFLICT (cpf) DO NOTHING;

INSERT INTO profissionais (nome, registro_conselho, especialidade, duracao_consulta_min) VALUES
    ('Dra. Ana Beatriz Rocha',  'CRM-SP 201001', 'CLINICO_GERAL', 30),
    ('Dr. Ricardo Menezes',     'CRM-SP 201002', 'CARDIOLOGIA',   45),
    ('Dra. Luciana Prado',      'CRM-SP 201003', 'PEDIATRIA',     30),
    ('Dr. Fábio Andrade',       'CRM-SP 201004', 'DERMATOLOGIA',  60)
ON CONFLICT (registro_conselho) DO NOTHING;

-- Consultas na semana que vem, calculadas a partir da data em que a migration roda.
-- date_trunc('week', ...) devolve a segunda-feira da semana atual no PostgreSQL; + 1 semana = próxima segunda.
INSERT INTO consultas (paciente_id, profissional_id, inicio, fim, status, motivo_cancelamento)
SELECT p.id, f.id, ini, ini + (f.duracao_consulta_min * interval '1 minute'), c.status, c.motivo
FROM (VALUES
    ('28476195346', 'CRM-SP 201001', interval '9 hours',                    'CONFIRMADA', NULL),
    ('73520814617', 'CRM-SP 201001', interval '10 hours',                   'AGENDADA',   NULL),
    ('41859206794', 'CRM-SP 201003', interval '1 day 14 hours',             'AGENDADA',   NULL),
    ('60293741840', 'CRM-SP 201002', interval '2 days 9 hours 30 minutes',  'AGENDADA',   NULL),
    ('15734086207', 'CRM-SP 201004', interval '3 days 11 hours',            'CANCELADA',  'Paciente viajou')
) AS c(cpf, registro, deslocamento, status, motivo)
JOIN pacientes p ON p.cpf = c.cpf
JOIN profissionais f ON f.registro_conselho = c.registro
CROSS JOIN LATERAL (SELECT date_trunc('week', now()) + interval '1 week' + c.deslocamento AS ini) AS t(ini)
WHERE NOT EXISTS (SELECT 1 FROM consultas WHERE paciente_id = p.id AND profissional_id = f.id);
