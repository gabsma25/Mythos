# Projeto: leitor digital personalizado para o Galaxy Z Fold8

Minha recomendação é desenvolver um aplicativo Android nativo, utilizando Kotlin, Jetpack Compose e bibliotecas especializadas em leitura de livros digitais.

A proposta é criar um leitor semelhante ao Kindle na aparência e na experiência de navegação, mas com maior controle sobre tipografia, cores, espaçamento, organização da biblioteca e redução de distrações.

O projeto pode funcionar 100% offline, sem anúncios, contas, servidores ou APIs externas obrigatórias. Os livros, mangás, configurações e registros de leitura permanecerão armazenados no próprio celular.

A pesquisa também confirma que existe uma biblioteca particularmente adequada: o [Readium Kotlin Toolkit](https://github.com/readium/kotlin-toolkit?utm_source=chatgpt.com), que oferece leitura de EPUB, navegação paginada e personalização tipográfica. A versão 3.2.0, lançada em maio de 2026, acrescentou transições animadas entre páginas de EPUB.&#x20;

[image](https://www.google.com/s2/favicons?domain=https://blog.readium.org\&sz=32)

Kotlin Toolkit Version 3.2.0

+1



O ponto mais importante do projeto será separar três experiências de leitura:

- EPUB: livros com texto reajustável, fontes personalizadas e aparência semelhante à de um Kindle.
- PDF: documentos que preservam sua diagramação original e exigem recursos próprios de zoom e navegação.
- Mangás: imagens sequenciais com diferentes direções de leitura, visualização de páginas duplas e rolagem vertical.

Essa separação permitirá implementar os recursos de cada formato sem criar um aplicativo excessivamente complexo ou pesado.

## 1. Funcionalidades recomendadas

Comparei recursos de projetos como [KOReader](https://github.com/koreader/koreader?utm_source=chatgpt.com), [Librera Reader](https://github.com/foobnix/LibreraReader?utm_source=chatgpt.com) e [Mihon](https://mihon.app/docs/guides/local-source/?utm_source=chatgpt.com). Eles oferecem referências interessantes para personalização, gerenciamento de arquivos locais e leitura de quadrinhos.&#x20;

[image](https://www.google.com/s2/favicons?domain=https://github.com\&sz=32)

GitHub

+2



### A. Leitor de livros e personalização

| Funcionalidade        | Comportamento desejado                                                   | Prioridade |
| --------------------- | ------------------------------------------------------------------------ | ---------- |
| Fontes personalizadas | Selecionar fontes instaladas no aplicativo ou importar arquivos de fonte | Alta       |
| Tamanho da fonte      | Ajustar sem perder a posição do texto                                    | Alta       |
| Espaçamento           | Controlar entrelinhas, parágrafos e letras                               | Alta       |
| Cores personalizadas  | Escolher fundo e texto de forma independente                             | Alta       |
| Margens               | Aumentar ou reduzir a largura útil de leitura                            | Alta       |
| Paginação             | Virar páginas por toque ou deslizando o dedo                             | Alta       |
| Animações             | Deslizamento suave, transição simples ou sem animação                    | Alta       |
| Colunas               | Uma ou duas colunas na tela interna                                      | Alta       |
| Alinhamento           | Texto à esquerda ou justificado                                          | Média      |
| Temas por livro       | Cada obra memoriza sua configuração de leitura                           | Média      |
| Busca no texto        | Localizar palavras ou trechos dentro do EPUB                             | Média      |
| Marcações             | Destacar trechos, registrar notas e salvar favoritos                     | Média      |

O Readium já oferece preferências para fonte, tamanho, altura das linhas, espaçamento, cores, margens e direção de leitura, entre outras. Portanto, boa parte desses recursos pode ser implementada sem desenvolver um mecanismo próprio de diagramação.&#x20;

[image](https://www.google.com/s2/favicons?domain=https://github.com\&sz=32)

GitHub



### B. Recursos de concentração e conforto visual

Este seria um dos diferenciais mais importantes do aplicativo.

Foco por linha

Destacar apenas a linha ou o parágrafo atual, reduzindo visualmente o restante do texto. A intensidade do destaque deve ser configurável.

Leitura imersiva

Ocultar cabeçalho, relógio, barra de status, navegação e controles do leitor. Um toque central revela as opções temporariamente.

Máscara de leitura

Mostrar somente uma região selecionada, com as demais linhas suavizadas. É uma alternativa ao foco por linha.

Leitura em voz alta

Utilizar o mecanismo Text-to-Speech (TTS) do Android, com reprodução de voz local quando o idioma estiver disponível offline.

Continuidade silenciosa

Ao reabrir um livro, retornar diretamente ao trecho anterior, sem notificações de metas, estatísticas invasivas ou mensagens de incentivo.

O Android permite ocultar as barras de sistema com `WindowInsetsControllerCompat`. Entretanto, ocultar essas barras não equivale a bloquear notificações. Para isso, o aplicativo poderá oferecer integração opcional com o modo Não Perturbe, respeitando as permissões e restrições do Android 17.&#x20;

[image](https://www.google.com/s2/favicons?domain=https://developer.android.com\&sz=32)

Android Developers

+1



As funções de foco visual devem ser opcionais. A legibilidade e os benefícios de cada estratégia variam entre pessoas; o aplicativo deve permitir testar diferentes configurações, não impor um único padrão.

## 2. Aproveitamento das duas telas do Galaxy Z Fold8

A Samsung descreve a tela interna do Fold8 como um painel de proporção 4:3, que se transforma em 3:4 quando rotacionado. Isso é especialmente interessante para leitura de conteúdo longo.&#x20;

[image](https://www.google.com/s2/favicons?domain=https://news.samsung.com\&sz=32)

Samsung Newsroom Brasil



Referência visual do Android: leitura paginada em duas colunas em dispositivo dobrável.

Exemplo de mangá ocupando a tela interna do Galaxy Z Fold8.

| Tela ou estado      | Comportamento do aplicativo                                     |
| ------------------- | --------------------------------------------------------------- |
| Externa, 5,5"       | Leitura em uma coluna, comandos discretos e navegação por toque |
| Interna, 7,6"       | Uma página centralizada ou duas colunas, conforme preferência   |
| Interna rotacionada | Recalcular margens, tamanho do texto e número de colunas        |
| Ao abrir ou fechar  | Preservar o trecho atual e adaptar a apresentação               |
| Multijanela         | Reajustar o leitor à área disponível, sem cortar conteúdo       |

Um detalhe técnico importante: a adaptação não deve depender diretamente dos 1.848 × 2.448 pixels da tela. No Android, interfaces utilizam medidas independentes de densidade (`dp`) e classes de tamanho de janela (`WindowSizeClass`).

A biblioteca `Material 3 Adaptive` permite identificar as dimensões disponíveis e características das dobradiças. Também é necessário preservar o estado da leitura durante mudanças de configuração.&#x20;

[image](https://www.google.com/s2/favicons?domain=https://developer.android.com\&sz=32)

Android Developers

+1



A continuidade entre telas deve ser testada no aparelho. A Samsung também disponibiliza controles de continuidade para a tela externa nas configurações do Fold.&#x20;

[image](https://www.google.com/s2/favicons?domain=https://www.samsung.com\&sz=32)

Samsung dk



## 3. Leitor de mangás e quadrinhos

Eu criaria um leitor independente do EPUB, aproveitando a mesma biblioteca de livros.

| Recurso                 | Implementação                                            |
| ----------------------- | -------------------------------------------------------- |
| CBZ                     | Ler imagens contidas em arquivos ZIP                     |
| Importação de capítulos | Adicionar arquivos ou diretórios de capítulos            |
| Direção de leitura      | Direita para esquerda, esquerda para direita ou vertical |
| Página dupla            | Exibir duas páginas quando houver espaço                 |
| Modo webtoon            | Rolagem vertical contínua                                |
| Ampliação               | Zoom por gesto de pinça e movimento da imagem            |
| Ajuste de imagem        | Ajustar por largura, altura ou preencher                 |
| Controle visual         | Brilho, fundo, filtros opcionais e ocultação de comandos |
| Progresso               | Registrar capítulo e página atual                        |

Para a primeira versão, CBZ é preferível a CBR, pois usa ZIP e é mais simples de processar. O próprio Readium ainda identifica o suporte a CBZ como parcial, portanto um leitor específico com `ZipFile` e `Coil` oferece mais controle.&#x20;

[image](https://www.google.com/s2/favicons?domain=https://github.com\&sz=32)

GitHub



Convém suportar tanto um CBZ contendo um volume inteiro quanto pastas com vários capítulos, mantendo a ordenação correta das imagens.

## 4. Biblioteca pessoal e importação de arquivos

A biblioteca será o ponto inicial do aplicativo. A interface poderá ser organizada por capas, com as categorias Lendo, Não iniciados, Concluídos, Favoritos e coleções criadas manualmente.

## Minha biblioteca

Exemplo conceitual da interface

Todos

Lendo

Mangás

Favoritos

Livro A

64%

Livro B

Novo

Mangá C

Cap. 4

Importar arquivos

A representação acima ilustra a estrutura funcional, não uma interface já implementada.

Para adicionar seus arquivos, eu implementaria três entradas: seleção pelo gerenciador de arquivos Android, compartilhamento de arquivos com o aplicativo e escolha de uma pasta da biblioteca.

O Android oferece o Storage Access Framework (SAF), com `ACTION_OPEN_DOCUMENT` para documentos e `ACTION_OPEN_DOCUMENT_TREE` para diretórios. Assim, não é necessário solicitar acesso irrestrito ao armazenamento do telefone.&#x20;

[image](https://www.google.com/s2/favicons?domain=https://developer.android.com\&sz=32)

Android Developers



Também recomendo uma opção para criar uma cópia dos arquivos importados no armazenamento privado do aplicativo. Dessa maneira, mover ou excluir um arquivo da pasta Downloads não interromperá a leitura. Como contrapartida, haverá consumo adicional de armazenamento.

## 5. Stack tecnológica recomendada

Para o desenvolvimento, escolheria a seguinte combinação:

| Camada          | Tecnologia                   | Justificativa                                               |
| --------------- | ---------------------------- | ----------------------------------------------------------- |
| Linguagem       | Kotlin                       | Linguagem principal para desenvolvimento Android nativo     |
| Interface       | Jetpack Compose + Material 3 | Componentes declarativos, animações e personalização visual |
| Telas dobráveis | Material 3 Adaptive          | Adaptação ao tamanho da janela e à dobradiça                |
| EPUB            | Readium Kotlin Toolkit 3.4.0 | Motor especializado em publicações digitais                 |
| PDF             | AndroidX PDF / PdfRenderer   | Renderização usando componentes do Android                  |
| Mangás          | ZipFile + Coil               | Leitura de arquivos CBZ e carregamento eficiente de imagens |
| Banco de dados  | Room (SQLite)                | Biblioteca, metadados, favoritos e progresso                |
| Preferências    | Jetpack DataStore            | Fontes, temas, margens e configurações globais              |
| Arquivos        | Storage Access Framework     | Importação de livros e acesso a pastas                      |
| Assincronismo   | Kotlin Coroutines + Flow     | Importações e operações sem bloquear a interface            |
| IDE             | Android Studio               | Compilação, emulação, depuração e instalação no Fold8       |

A Room é recomendada pelo Android como uma abstração sobre SQLite, enquanto o DataStore é adequado para preferências pequenas. Para imagens grandes, o Coil ajuda a controlar cache e consumo de memória.&#x20;

[image](https://www.google.com/s2/favicons?domain=https://developer.android.com\&sz=32)

Android Developers

+2



### Um cuidado especial com o PDF

Existem duas alternativas relevantes:

AndroidX PDF: a versão `1.0.0-beta01`, publicada em agosto de 2026, oferece componentes para visualização, pesquisa textual, seleção e zoom. Exige no mínimo Android 12 com SDK Extension 13. Para o Fold8, é uma opção interessante, mas ainda está em estágio beta.&#x20;

[image](https://www.google.com/s2/favicons?domain=https://developer.android.com\&sz=32)

Android Developers

+2



PdfRenderer: API do Android para renderizar páginas PDF. Permite maior controle sobre a paginação personalizada, mas exige que você implemente boa parte da interface.

Eu começaria testando o AndroidX PDF. Se suas limitações visuais impedirem uma experiência semelhante à do Kindle, utilizaria `PdfRenderer` para construir um leitor mais específico.

O Readium também possui um adaptador PDFium, mas a própria documentação registra problemas de manutenção da dependência. Não seria minha primeira escolha para um projeto novo.&#x20;

[image](https://www.google.com/s2/favicons?domain=https://github.com\&sz=32)

GitHub



Limitação importante: alterar a fonte e o espaçamento de um EPUB é simples porque seu texto pode ser reorganizado (reflow). Um PDF tradicional possui diagramação fixa. Para obter a mesma personalização em PDF, seria necessário implementar extração de texto e um modo de leitura reformatada. Documentos digitalizados podem ainda exigir OCR, o que aumentaria a complexidade.

## 6. Arquitetura do aplicativo

Sugiro MVVM (Model–View–ViewModel) com uma camada de repositórios (Repository Pattern).

Não é necessário começar com múltiplos módulos Gradle, microsserviços ou uma arquitetura complexa. Um projeto Android com pacotes bem separados é suficiente.

Interface — Jetpack Compose

Biblioteca, leitor, configurações e navegação

ViewModel

Estado da interface, eventos e operações de leitura

Repositories

Acesso aos livros, progresso, preferências e arquivos

Room

Metadados

DataStore

Preferências

Arquivos

EPUB, PDF, CBZ

A estrutura inicial de diretórios poderia ser:

```

app/src/main/java/com/seuleitor/
├── data/
│   ├── database/
│   ├── repository/
│   ├── preferences/
│   └── importer/
├── model/
│   ├── Book.kt
│   ├── ReadingProgress.kt
│   └── ReaderPreferences.kt
├── reader/
│   ├── epub/
│   ├── pdf/
│   └── comic/
├── ui/
│   ├── library/
│   ├── reader/
│   ├── settings/
│   └── theme/
└── MainActivity.kt

```

Cada formato terá seu próprio mecanismo de renderização, mas compartilhará a biblioteca e o sistema de progresso.

Para EPUB, o progresso deve ser salvo utilizando um localizador de texto (Readium Locator), e não apenas o número da página. Isso é essencial porque a quantidade de páginas muda quando você aumenta a fonte ou abre o livro em outra tela.

Há também um detalhe de integração: o navegador EPUB estável do Readium utiliza `Fragment`. Ele pode ser integrado a uma interface Compose pelo componente `AndroidFragment`, com a configuração adequada da `FragmentFactory`. Portanto, não é necessário abandonar o Compose.&#x20;

[image](https://www.google.com/s2/favicons?domain=https://developer.android.com\&sz=32)

Android Developers

+1



## 7. Plano de desenvolvimento

Eu dividiria o trabalho em quatro etapas incrementais. A primeira deve resultar em um aplicativo realmente utilizável, não apenas uma demonstração.

Etapa 1 — MVP de leitura EPUB

Criar o projeto Android, importar EPUB, exibir a biblioteca, abrir livros, controlar fontes e cores, implementar paginação e salvar a posição. Testar a transição entre as duas telas do Fold8.

Etapa 2 — PDF e gerenciamento da biblioteca

Adicionar PDF, zoom, arquivos múltiplos, capas, pesquisa por título, coleções e importação de diretórios. Implementar exportação de dados para backup.

Etapa 3 — Mangás

Implementar CBZ, organização por volumes e capítulos, direção japonesa de leitura, modo webtoon, página dupla e cache eficiente de imagens.

Etapa 4 — Personalização avançada

Adicionar foco por linha, máscara de leitura, TTS, perfis tipográficos, destaques e notas. Refinar animações, acessibilidade e desempenho.

## 8. Requisitos de desempenho e privacidade

Como a ideia é utilizar o aplicativo diariamente, eu adotaria os seguintes critérios técnicos desde o início:

| Requisito               | Decisão de implementação                                                                         |
| ----------------------- | ------------------------------------------------------------------------------------------------ |
| Sem conexão obrigatória | Não incluir dependências de servidor, login ou autenticação                                      |
| Sem anúncios            | Não utilizar SDKs de publicidade nem análise comportamental                                      |
| Privacidade             | Armazenar dados localmente; dispensar a permissão `INTERNET` enquanto não houver recurso de rede |
| Consumo de memória      | Carregar somente as páginas e imagens necessárias, usando cache limitado                         |
| Bateria                 | Evitar tarefas periódicas, sincronizações e animações permanentes                                |
| Progresso               | Salvar de forma persistente e restaurar após fechamento ou interrupção do aplicativo             |
| Backup                  | Exportar a biblioteca e as configurações para não perder tudo em uma desinstalação               |
| Portabilidade           | Usar formatos abertos e acessíveis sem serviços proprietários                                    |

Também não tentaria implementar de início suporte a livros Kindle com DRM, sincronização entre dispositivos, reconhecimento de texto em PDF digitalizado ou recursos de inteligência artificial. Esses itens ampliariam consideravelmente o escopo sem contribuir diretamente para a primeira versão.

## 9. Como desenvolver e instalar no próprio Fold8

Você poderá realizar todo o desenvolvimento no computador Windows com Android Studio e usar o Fold8 como dispositivo de testes.

O processo é direto: criar um projeto Kotlin com Compose, ativar a depuração USB nas opções do desenvolvedor, conectar o celular e executar o aplicativo pelo Android Studio. Também é possível gerar um APK e instalá-lo diretamente, sem publicar na Google Play. O ADB permite instalação pelo comando `adb install`.&#x20;

[image](https://www.google.com/s2/favicons?domain=https://developer.android.com\&sz=32)

Android Developers



Como o Fold8 utiliza Android 17/One UI 9, você pode direcionar os testes especificamente a essa versão, usando as APIs modernas, sem assumir o custo de compatibilidade com aparelhos Android muito antigos.&#x20;

[image](https://www.google.com/s2/favicons?domain=https://news.samsung.com\&sz=32)

Samsung Newsroom Brasil

+1



No desenvolvimento, eu validaria especialmente a permanência no mesmo trecho após dobrar ou desdobrar o aparelho, a abertura de PDFs grandes, a fluidez da paginação e a ausência de travamentos com mangás de imagens grandes.

## 10. Documentação para iniciar

[image](https://www.google.com/s2/favicons?domain=https://github.com\&sz=32)

[Readium Kotlin Toolkit](https://github.com/readium/kotlin-toolkit?utm_source=chatgpt.com)

Código-fonte, aplicativo de teste, EPUB, preferências tipográficas e navegadores.

[image](https://www.google.com/s2/favicons?domain=https://developer.android.com\&sz=32)

[Android — interfaces para dispositivos dobráveis](https://developer.android.com/develop/adaptive-apps/guides/foldables/make-your-app-fold-aware?utm_source=chatgpt.com)

Classes de janela, mudanças de postura e adaptação de layouts.

[image](https://www.google.com/s2/favicons?domain=https://developer.android.com\&sz=32)

[Android — PDF Viewer](https://developer.android.com/develop/ui/views/layout/pdf/pdf-viewer?utm_source=chatgpt.com)

Renderização nativa de PDF e funcionalidades disponíveis.

[image](https://www.google.com/s2/favicons?domain=https://mihon.app\&sz=32)

[Mihon — biblioteca local de mangás](https://mihon.app/docs/guides/local-source/?utm_source=chatgpt.com)

Referência de estrutura de diretórios e organização de capítulos.

## Recomendação para a primeira implementação

Eu começaria com um aplicativo exclusivamente EPUB, com biblioteca local, interface imersiva e paginação animada, assegurando que a mesma obra possa ser lida nas duas telas sem perder a posição.

Essa primeira versão já resolveria parte importante da necessidade de leitura personalizada e permitiria avaliar o conforto visual diretamente no Fold8. Depois, acrescentaria PDF e mangás sobre a arquitetura existente.

A vantagem desse caminho é desenvolver competências reais em Android nativo, Kotlin, Compose, persistência e gestão de arquivos, sem gastar tempo inicialmente com infraestrutura que um aplicativo pessoal não precisa.

A decisão técnica mais importante é utilizar mecanismos de leitura existentes e concentrar seu desenvolvimento na experiência de uso. É justamente na personalização e na adaptação ao Fold8 que seu aplicativo poderá se diferenciar dos leitores tradicionais.