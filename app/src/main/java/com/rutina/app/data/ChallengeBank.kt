package com.rutina.app.data

/**
 * Contenido de los retos que no necesitan IA ni internet: textos en inglés para leer en voz alta,
 * mini retos de JavaScript con sus tests y lecturas con preguntas.
 */
object ChallengeBank {

    val ENGLISH = listOf(
        "Every morning I try to start the day with a short walk. The fresh air helps me think clearly, and I feel more awake when I sit down to work. On busy days it is not easy, but even ten minutes make a big difference.",
        "Learning a new programming language takes time and patience. At first the syntax looks strange, and simple tasks feel difficult. However, if you practise a little every day, you will notice that the code starts to make sense.",
        "My favourite place in the city is a small park near the river. On Sunday afternoons there are families having picnics, people reading under the trees and children playing football. It is quiet, green and very relaxing.",
        "A good job interview is like a conversation. The interviewer wants to know how you solve problems, how you work in a team and what you have learnt from your mistakes. Being honest is more important than knowing everything.",
        "Yesterday I cooked dinner for my friends. I made a vegetable soup, a big salad and a chocolate cake. Everybody said the food was delicious, although the cake was a bit too sweet. Next time I will use less sugar.",
        "Sleeping well is one of the best things you can do for your health. Adults should sleep between seven and nine hours every night. Looking at your phone in bed can make it harder to fall asleep, so try to leave it in another room.",
        "When I travel, I prefer trains to planes. You can see the landscape through the window, walk around and read a book without interruptions. Trains are also better for the environment, and the stations are usually in the city centre.",
        "Web applications today are built with components. Each component has its own template, styles and logic, and you can reuse it in different parts of the project. This makes large applications easier to understand and maintain.",
        "Last summer I decided to learn how to swim properly. I was afraid of deep water, so I took lessons with a patient teacher. After two months I could swim across the pool without stopping, and I felt really proud of myself.",
        "Many people work from home now. It saves time because you do not have to travel to the office, but it can also be lonely. To stay motivated, it helps to have a fixed routine, a quiet desk and regular breaks.",
        "Exercise does not have to be boring. You can dance in your living room, ride a bike to the supermarket or play a sport with friends. The most important thing is to find an activity you enjoy and do it regularly.",
        "Before a deadline, I write a list of everything I need to finish. Then I order the tasks from the most important to the least important. Crossing items off the list makes me feel productive and less stressed.",
    )

    class Test(val argsJson: String, val expectedJson: String)

    class Kata(val title: String, val description: String, val function: String, val starter: String, val tests: List<Test>)

    val KATAS = listOf(
        Kata(
            "Suma de pares", "Devuelve la suma de los números pares de la lista.", "sumaPares",
            "function sumaPares(nums) {\n  \n}",
            listOf(Test("[[1,2,3,4]]", "6"), Test("[[]]", "0"), Test("[[-2,5,8]]", "6")),
        ),
        Kata(
            "Invertir texto", "Devuelve el texto al revés.", "invertir",
            "function invertir(texto) {\n  \n}",
            listOf(Test("[\"hola\"]", "\"aloh\""), Test("[\"\"]", "\"\""), Test("[\"Vue 3\"]", "\"3 euV\"")),
        ),
        Kata(
            "¿Palíndromo?", "Devuelve true si el texto se lee igual al revés, sin tener en cuenta mayúsculas ni espacios.", "esPalindromo",
            "function esPalindromo(texto) {\n  \n}",
            listOf(Test("[\"Ana\"]", "true"), Test("[\"Anita lava la tina\"]", "true"), Test("[\"Angular\"]", "false")),
        ),
        Kata(
            "Contar vocales", "Devuelve cuántas vocales (a, e, i, o, u, sin tildes) tiene el texto, en mayúscula o minúscula.", "contarVocales",
            "function contarVocales(texto) {\n  \n}",
            listOf(Test("[\"TypeScript\"]", "2"), Test("[\"Angular\"]", "3"), Test("[\"\"]", "0")),
        ),
        Kata(
            "El mayor", "Devuelve el número más grande de la lista (tiene al menos uno).", "maximo",
            "function maximo(nums) {\n  \n}",
            listOf(Test("[[3,9,2]]", "9"), Test("[[-5,-1]]", "-1"), Test("[[7]]", "7")),
        ),
        Kata(
            "FizzBuzz", "Devuelve una lista del 1 a n donde los múltiplos de 3 son \"Fizz\", los de 5 \"Buzz\" y los de ambos \"FizzBuzz\".", "fizzBuzz",
            "function fizzBuzz(n) {\n  \n}",
            listOf(
                Test("[5]", "[1,2,\"Fizz\",4,\"Buzz\"]"),
                Test("[15]", "[1,2,\"Fizz\",4,\"Buzz\",\"Fizz\",7,8,\"Fizz\",\"Buzz\",11,\"Fizz\",13,14,\"FizzBuzz\"]"),
                Test("[1]", "[1]"),
            ),
        ),
        Kata(
            "Sin repetidos", "Devuelve la lista sin elementos repetidos, en el orden en que aparecen por primera vez.", "unicos",
            "function unicos(lista) {\n  \n}",
            listOf(Test("[[1,2,2,3,1]]", "[1,2,3]"), Test("[[\"a\",\"b\",\"a\"]]", "[\"a\",\"b\"]"), Test("[[]]", "[]")),
        ),
        Kata(
            "Mayúsculas", "Devuelve la frase con la primera letra de cada palabra en mayúscula.", "capitalizar",
            "function capitalizar(frase) {\n  \n}",
            listOf(Test("[\"hola mundo\"]", "\"Hola Mundo\""), Test("[\"vue y angular\"]", "\"Vue Y Angular\""), Test("[\"\"]", "\"\"")),
        ),
        Kata(
            "Contar palabras", "Devuelve cuántas palabras tiene la frase. Puede haber espacios de más.", "contarPalabras",
            "function contarPalabras(frase) {\n  \n}",
            listOf(Test("[\"  hola   que tal \"]", "3"), Test("[\"\"]", "0"), Test("[\"TypeScript\"]", "1")),
        ),
        Kata(
            "Factorial", "Devuelve el factorial de n (n! = 1 · 2 · … · n, y 0! = 1).", "factorial",
            "function factorial(n) {\n  \n}",
            listOf(Test("[0]", "1"), Test("[5]", "120"), Test("[10]", "3628800")),
        ),
        Kata(
            "Aplanar", "Recibe una lista de listas y devuelve una sola lista con todo (solo un nivel).", "aplanar",
            "function aplanar(lista) {\n  \n}",
            listOf(Test("[[[1,2],[3],[]]]", "[1,2,3]"), Test("[[[1,[2]],[3]]]", "[1,[2],3]"), Test("[[]]", "[]")),
        ),
        Kata(
            "Mayores de edad", "Recibe personas {nombre, edad} y devuelve los nombres de las que tienen 18 o más.", "mayoresDeEdad",
            "function mayoresDeEdad(personas) {\n  \n}",
            listOf(
                Test("[[{\"nombre\":\"Ana\",\"edad\":20},{\"nombre\":\"Leo\",\"edad\":15},{\"nombre\":\"Sol\",\"edad\":18}]]", "[\"Ana\",\"Sol\"]"),
                Test("[[]]", "[]"),
            ),
        ),
        Kata(
            "Suma de dígitos", "Devuelve la suma de los dígitos de un número entero positivo.", "sumaDigitos",
            "function sumaDigitos(n) {\n  \n}",
            listOf(Test("[123]", "6"), Test("[0]", "0"), Test("[9999]", "36")),
        ),
        Kata(
            "Ocurrencias", "Devuelve cuántas veces aparece la letra en el texto.", "ocurrencias",
            "function ocurrencias(texto, letra) {\n  \n}",
            listOf(Test("[\"banana\",\"a\"]", "3"), Test("[\"Vue\",\"x\"]", "0"), Test("[\"aaa\",\"a\"]", "3")),
        ),
    )

    class Question(val text: String, val options: List<String>, val correct: Int)

    class Reading(val title: String, val text: String, val questions: List<Question>)

    val READINGS = listOf(
        Reading(
            "El event loop de JavaScript",
            "JavaScript ejecuta el código en un solo hilo: solo hace una cosa a la vez. Para no quedarse bloqueado esperando " +
                "(una petición a un servidor, un temporizador…), delega esas esperas en el entorno (el navegador o Node.js). " +
                "Cuando terminan, su callback se pone en una cola. El event loop vigila la pila de llamadas y, cuando está vacía, " +
                "saca el siguiente callback de la cola y lo ejecuta.\n\nHay dos colas con distinta prioridad. Las microtareas, como " +
                "los .then() de las promesas, se ejecutan todas en cuanto se vacía la pila, antes que cualquier tarea normal. Las " +
                "macrotareas, como los setTimeout, van después. Por eso un setTimeout(fn, 0) no se ejecuta inmediatamente: tiene que " +
                "esperar a que termine el código actual y a que se vacíe la cola de microtareas.",
            listOf(
                Question("¿Cuántos hilos usa JavaScript para ejecutar tu código?", listOf("Uno", "Dos", "Uno por cada función", "Depende del navegador"), 0),
                Question("¿Qué se ejecuta antes?", listOf("Un setTimeout(fn, 0)", "El .then() de una promesa ya resuelta", "Siempre lo que se escribió primero", "Los dos a la vez"), 1),
                Question("¿Cuándo saca el event loop un callback de la cola?", listOf("Cada milisegundo", "Cuando la pila de llamadas está vacía", "Cuando la cola está llena", "Nunca: lo ejecuta el servidor"), 1),
            ),
        ),
        Reading(
            "var, let y const",
            "En JavaScript hay tres formas de declarar variables. var es la antigua: su ámbito es la función entera (no el bloque) " +
                "y su declaración se «eleva» al principio de la función (hoisting), así que se puede usar antes de la línea donde " +
                "se declara, aunque valdrá undefined.\n\nlet y const llegaron con ES2015 y tienen ámbito de bloque: solo existen " +
                "dentro de las llaves donde se declaran. Si se usan antes de declararlas dan error, porque están en la «zona muerta " +
                "temporal». La diferencia entre ellas es que una variable const no se puede reasignar. Ojo: const no hace el valor " +
                "inmutable. Si guardas un objeto o un array en una const, puedes cambiar su contenido; lo que no puedes es hacer " +
                "que la variable apunte a otro objeto.",
            listOf(
                Question("¿Qué ámbito tiene una variable declarada con var?", listOf("El bloque", "La función", "El archivo", "Global siempre"), 1),
                Question("Con const lista = [1, 2], ¿qué se puede hacer?", listOf("lista = [3]", "lista.push(3)", "Nada: es inmutable", "Redeclararla con let"), 1),
                Question("¿Qué pasa si usas una variable let antes de declararla?", listOf("Vale undefined", "Vale null", "Da un error", "Se convierte en var"), 2),
            ),
        ),
        Reading(
            "type e interface en TypeScript",
            "En TypeScript puedes describir la forma de un objeto con interface o con type. En muchos casos son intercambiables, " +
                "pero hay diferencias. Una interface se puede ampliar con extends y, además, si declaras dos interfaces con el mismo " +
                "nombre, TypeScript las fusiona en una sola (declaration merging). Eso es útil, por ejemplo, para añadir propiedades " +
                "a tipos de librerías.\n\nUn type es un alias para cualquier tipo, no solo para objetos: puede ser una unión " +
                "(\"ok\" | \"error\"), una tupla o un tipo calculado. Los type no se fusionan: declarar dos con el mismo nombre da " +
                "error. Para combinarlos se usa la intersección con &. Una regla práctica habitual es usar interface para describir " +
                "objetos y clases, y type para uniones y tipos más complejos.",
            listOf(
                Question("¿Qué ocurre si declaras dos interfaces con el mismo nombre?", listOf("Da error", "Se fusionan", "Gana la última", "Gana la primera"), 1),
                Question("¿Cuál de estos solo se puede expresar con type?", listOf("Un objeto con nombre y edad", "Una clase", "La unión \"ok\" | \"error\"", "Un objeto que extiende otro"), 2),
                Question("¿Cómo se combinan dos type?", listOf("Con extends", "Con la intersección &", "Con implements", "No se pueden combinar"), 1),
            ),
        ),
        Reading(
            "Componentes y servicios en Angular",
            "En Angular la interfaz se construye con componentes: cada uno tiene una plantilla HTML, sus estilos y una clase con " +
                "la lógica. Los componentes deberían ocuparse sobre todo de mostrar datos y responder al usuario. La lógica que se " +
                "comparte entre varios componentes, como las llamadas a una API, se pone en servicios.\n\nUn servicio es una clase " +
                "marcada con @Injectable. Angular la crea y la entrega a quien la necesite mediante la inyección de dependencias: " +
                "el componente la pide en su constructor (o con la función inject) y no la crea él mismo con new. Si el servicio se " +
                "registra con providedIn: 'root', Angular crea una única instancia para toda la aplicación, así que todos los " +
                "componentes comparten los mismos datos.",
            listOf(
                Question("¿Dónde se pone la lógica compartida, como las llamadas a la API?", listOf("En la plantilla", "En los estilos", "En un servicio", "En el index.html"), 2),
                Question("¿Cómo consigue un componente un servicio?", listOf("Con new", "Por inyección de dependencias", "Importando el HTML", "Con un evento"), 1),
                Question("¿Qué significa providedIn: 'root'?", listOf("Una instancia por componente", "Una única instancia para toda la app", "Que solo lo usa el componente raíz", "Que se crea en el servidor"), 1),
            ),
        ),
        Reading(
            "Reactividad en Vue 3",
            "Vue actualiza la pantalla sola cuando cambian los datos reactivos. En la Composition API hay dos formas principales de " +
                "crearlos. ref() envuelve cualquier valor, también primitivos como números o textos; en el código se lee y se cambia " +
                "con .value, aunque en la plantilla se escribe sin .value porque Vue lo desenvuelve solo.\n\nreactive() solo acepta " +
                "objetos (incluidos arrays) y devuelve un proxy: se usan sus propiedades directamente, sin .value. Su pega es que, si " +
                "desestructuras el objeto, las variables resultantes pierden la reactividad. Para valores derivados de otros se usa " +
                "computed(), que se recalcula solo cuando cambian sus dependencias y guarda el resultado mientras tanto.",
            listOf(
                Question("¿Cómo se cambia el valor de const n = ref(0) en el script?", listOf("n = 1", "n.value = 1", "n.set(1)", "reactive(n, 1)"), 1),
                Question("¿Qué pasa al desestructurar un objeto creado con reactive()?", listOf("Nada especial", "Las variables pierden la reactividad", "Da un error", "Se convierte en ref"), 1),
                Question("¿Para qué sirve computed()?", listOf("Para hacer peticiones HTTP", "Para valores derivados que se recalculan solos", "Para crear componentes", "Para estilos"), 1),
            ),
        ),
        Reading(
            "Promesas y async/await",
            "Una promesa representa un valor que llegará en el futuro. Empieza pendiente y acaba cumplida (con un valor) o rechazada " +
                "(con un error). Con .then() se indica qué hacer con el valor y con .catch() qué hacer si falla.\n\nasync/await es una " +
                "forma más legible de trabajar con promesas. Una función marcada con async siempre devuelve una promesa. Dentro de " +
                "ella, await pausa esa función hasta que la promesa se resuelve, sin bloquear el resto del programa. Los errores se " +
                "capturan con un try/catch normal. Si tienes varias peticiones independientes, es mejor lanzarlas a la vez con " +
                "Promise.all que esperarlas una detrás de otra con varios await seguidos, porque así tardan solo lo que la más lenta.",
            listOf(
                Question("¿Qué devuelve siempre una función async?", listOf("undefined", "Una promesa", "El valor directamente", "Un callback"), 1),
                Question("¿Cómo se capturan los errores con await?", listOf("Con .then()", "Con try/catch", "No se pueden capturar", "Con setTimeout"), 1),
                Question("Tienes 3 peticiones independientes. ¿Qué es más rápido?", listOf("Tres await seguidos", "Promise.all", "Un bucle con setTimeout", "Es igual"), 1),
            ),
        ),
    )

    /** Elige un elemento que no haya salido hace poco (y lo apunta como usado). */
    fun pick(prefs: Prefs, kind: String, size: Int): Int {
        val recent = prefs.recentBankItems
        val fresh = (0 until size).filter { "$kind:$it" !in recent }
        val chosen = (fresh.ifEmpty { (0 until size).toList() }).random()
        prefs.recentBankItems = recent.filterNot { it == "$kind:$chosen" } + "$kind:$chosen"
        return chosen
    }
}
