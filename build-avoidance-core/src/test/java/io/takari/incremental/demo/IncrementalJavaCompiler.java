package io.takari.incremental.demo;

import io.takari.incremental.FileSet;
import io.takari.incremental.internal.DefaultBuildContext;
import io.takari.incremental.internal.DefaultInput;
import io.takari.incremental.internal.DefaultOutput;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class IncrementalJavaCompiler {

  /** @Injected */
  DefaultBuildContext context;

  /** Inputs waiting to be compiled */
  Set<DefaultInput> queue = new HashSet<>();

  /**
   * Compiled inputs, used to prevent multiple recompilation of the same input
   */
  Set<DefaultInput> processed = new HashSet<>();

  public void compile(Collection<FileSet> sourceSets) {

    // incremental compilation is a multi-pass process

    // initial compilation round needs to compile all modified inputs
    // and all inputs that depend on outputs orphaned since previous build

    // subsequent rounds need to compile inputs that depend on modified outputs
    // until there are no more new inputs to compile.
    // the worst case, all inputs are compiled. never endless loop
    // keeping track of all compiled inputs requires lots of ram

    for (FileSet sourceSet : sourceSets) {

      // context.getInputsForProcessing is a shortcut for iterating over all Files in the FileSet
      // and picking Inputs that require processing. It may provide better performance, especially
      // inside Eclipse, compared to manual implementation of the same logic

      for (DefaultInput input : context.registerInputsForProcessing(sourceSet)) {
        queue.add(input);
      }
    }

    // at this point all inputs were registered with the build context and build-avoidance can
    // determine and delete all "orphaned" outputs, i.e. outputs that were produced from inputs
    // that no longer exist or not part of compiler configuration
    for (DefaultOutput deleted : context.deleteStaleOutputs()) {
      // find and enqueue all affected inputs
      enqueueAffectedInputs(deleted);
    }

    while (!queue.isEmpty()) {
      // convert inputs collection to format expected by the compiler

      // make sure the same input is not processed multiple times
      processed.addAll(queue);

      queue.clear(); // additional sources may be put to compile queue

      // invoke the compiler, compilation results are reported back through #acceptResult below
    }
  }

  private void enqueueAffectedInputs(DefaultOutput output) {
    // Ideally, API should expose both new and old capabilities provided by the output
    // this is not necessary for Java because type/simpleType are tightly coupled to
    // output identity and do not change from one build to the next

    for (String type : output.getCapabilities("jdt.type")) {
      for (DefaultInput input : context.getDependencies("jdt.type", type)) {
        enqueue(input);
      }
    }

    for (String type : output.getCapabilities("jdt.simpleType")) {
      for (DefaultInput input : context.getDependencies("jdt.simpleType", type)) {
        enqueue(input);
      }
    }
  }

  // simplified for sake of brevity, real incremental compiler allows multiple classes per input
  public void acceptResult(DefaultInput input, byte[] bytes, String type, String simpleType,
      String[] referencedTypes, String[] referencedSimpleTypes) throws IOException {

    File outputFile = getOutputFile(type);

    DefaultOutput output = input.registerOutput(outputFile);

    // register capabilities provided by this output
    // used to determine affected inputs when the output changed or deleted
    output.addCapability("jdt.type", type);
    output.addCapability("jdt.simpleType", simpleType);

    if (hasStructuralChanges(outputFile, bytes)) {
      // enqueue other sources that depend on type/simpleType provided by this class
      // unless they were already compiled, that is
      enqueueAffectedInputs(output);
    }

    // register requirements of this input
    // these will be matched with output capabilities to find affected inputs
    for (String referencedType : referencedTypes) {
      input.addRequirement("jdt.type", referencedType);
    }
    for (String referencedSimpleType : referencedSimpleTypes) {
      input.addRequirement("jdt.simpleType", referencedSimpleType);
    }

    // this is new Output instance, that does not know anything about the previous build
    try (OutputStream os = output.newOutputStream()) {
      os.write(bytes);
    }
  }

  private void enqueue(DefaultInput input) {
    if (processed.add(input)) {
      queue.add(input);
    }
  }

  private boolean hasStructuralChanges(File outputFile, byte[] bytes) {
    // TODO Auto-generated method stub
    return false;
  }

  private File getOutputFile(String type) {
    // TODO Auto-generated method stub
    return null;
  }
}
