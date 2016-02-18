import groovy.json.JsonBuilder
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.io.FileType

String brandDirectory
String overwriteFilePath = 'data/overwrite.json'
File overwriteFile = new File(overwriteFilePath)
def directoryToProcess
def overwriteJson
boolean testMode = false

JsonFileHandler jsonFileHandler = new JsonFileHandler()

// TODO: map with list of required JSON properties by object type (i.e. Benefit, Plan, TierOption)


def cli = new CliBuilder(usage: 'groovy demo-json-data-updater.groovy [options]',header: 'Options:')

cli.with {
    d longOpt: 'brandDir', args: 1, argName: 'brandDirectory', "Directory with brands containing demo data"
}

cli.t('Run in test mode. Writes json to output directory (optional)')

def options = cli.parse(args)

if (options.'brandDir') {
    brandDirectory = options.'brandDir'
    directoryToProcess = new File(brandDirectory)
}

if (!brandDirectory) {
    cli.usage()
    System.exit(-1)
} else if (!directoryToProcess.exists()) {
    println "Unable to find Directory: $inputDirectory"
    cli.usage()
    System.exit(-1)
}

if (options.'test') {
	testMode = true
}

if (!overwriteFile.exists()) {
    println "\nWARNING: Unable to find JSON overwrite file: ${overwriteFilePath}"
    System.exit(-1)
}

println "Reading overwrite JSON file: ${overwriteFile.name}"
overwriteJson = jsonFileHandler.read(overwriteFile)
println "\n\toverwrite json -> ${overwriteJson}"

println "\nProcessing directory -> ${brandDirectory}"
println '...................................\n'

directoryToProcess.eachFile FileType.DIRECTORIES, { directory ->
	String outputPath =  "output/${directory.name}_benefit.json"
	println "\nProcessing 'benefit.json' files in ${directory.name}"
	directory.eachFileMatch(~/benefit\.json/) { file ->
		def jsonData = jsonFileHandler.read(file)		

		def jsonOutput = updateJson(jsonData, overwriteJson)

		if (testMode) {
			println "\nupdating... ${outputPath}"
			jsonFileHandler.write(jsonOutput, new File(outputPath))
			println "\n${new JsonBuilder(jsonOutput).toPrettyString()}"
		} else {
			println "\nupdating... ${file.canonicalPath}"
			jsonFileHandler.write(jsonOutput, file)
		}
	}
	println "\n---------------------------"
}

def updateJson(def json, def overwrite) {	
	def keys = overwrite.keySet()
	def benefitKeys = keys as List
	def planKeys
	def tierOptions = []
	if (benefitKeys.find{ it == 'plans'}) {
		benefitKeys.remove('plans')
		planKeys = overwrite['plans'][0].keySet() as List
		if (planKeys.contains('tierOptions')) {
			def tierOptionMap = [:]
			overwrite['plans'][0].tierOptions.each { tierOption ->
				def data = [:]
				tierOption.each { k, v ->
					if (k == 'tierOptionInternalCode') {
						tierOptionMap[k] = v	
					} else {
						data[k] = v
					}
				}
				tierOptionMap.data = data
			}
			tierOptions << tierOptionMap
		}
	}

	println "tierOptions::$tierOptions"

	// Update benefit data
	benefitKeys.each { k ->
		json[k] = overwrite[k]
	}

	// Update plan data
	tierOptions.each { tierOption ->		
		json.plans.each { plan ->
			plan.tierOptions.each { tOpt ->
				if (tOpt['tierOptionInternalCode'] == tierOption['tierOptionInternalCode']) {
					tierOption['data'].each { k, v ->
						tOpt[k] = v
					}
				}
			}
		}
	}		

	println "\nkeys: $keys"
	println "\tbenefit keys: $benefitKeys"
	println "\tplan keys: $planKeys"
	println "\ttier options: $tierOptions"

	return json
}

println '\nDONE'
