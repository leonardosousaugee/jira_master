package com.juditecompany.jiramaster.exception;

import java.util.List;

/**
 * Modelo sem tarifa configurada. Nao se assume um padrao: gravar custo errado e pior do que
 * recusar a gravar, porque o numero errado autoriza gasto que nao deveria ser autorizado.
 */
public class ModeloDesconhecidoException extends RuntimeException {

    private final List<String> modelosConhecidos;

    public ModeloDesconhecidoException(String modelo, List<String> modelosConhecidos) {
        super("Modelo \"" + modelo + "\" nao tem tarifa configurada");
        this.modelosConhecidos = modelosConhecidos;
    }

    public List<String> getModelosConhecidos() {
        return modelosConhecidos;
    }
}
