package com.calsignlabs.apde.build.dag;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.calsignlabs.apde.build.Compiler;
import com.calsignlabs.apde.build.TextTransform;

import org.eclipse.jdt.core.compiler.IProblem;

public class CompileBuildTask extends ArgLambdaBuildTask {
	public CompileBuildTask(Getter<File> libs, Getter<File> androidJar, Getter<File> src,
							Getter<File> gen, Getter<File> binClasses, BuildTask... deps) {
		super(context -> {
			Compiler compiler = new Compiler(context.isCustomProblems());
			
			List<String> args = new ArrayList<String>();
			
			if (context.isVerbose()) {
				args.add("-verbose");
			} else {
				args.add("-warn:-unusedImport");
			}
			
			addAll(args, "-extdirs", libs.get(context).getAbsolutePath());
			addAll(args, "-bootclasspath", androidJar.get(context).getAbsolutePath());
			addAll(args, "-classpath", src.get(context).getAbsolutePath()
					+ ":" + gen.get(context).getAbsolutePath()
					+ ":" + libs.get(context).getAbsolutePath());
			addAll(args, "-1.6", "-target", "1.6");
			args.add("-proc:none");
			addAll(args, "-d", binClasses.get(context).getAbsolutePath());
			args.add(src.get(context).getAbsolutePath() + "/");
			args.add(gen.get(context).getAbsolutePath() + "/");
			
			boolean success = compiler.compile(argsArray(args)) && !context.getPreprocessor().hasSyntaxErrors();
			
			if (context.isCustomProblems()) {
				try {
					for (IProblem problem : compiler.getProblems()) {
						context.getProblems().add(context.getPreprocessor().buildCompilerProblem(problem));
					}
				} catch (TextTransform.LockException e) {
					e.printStackTrace();
				}
			}
			
			return success;
		}, "Compiling", deps);
		
		orGetterChangeNoticer(libs, androidJar, src, gen, binClasses);
	}
}
